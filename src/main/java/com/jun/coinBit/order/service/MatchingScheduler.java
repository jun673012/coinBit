package com.jun.coinBit.order.service;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.balance.entity.BalanceId;
import com.jun.coinBit.coin.entity.Coin;
import com.jun.coinBit.coin.service.CoinService;
import com.jun.coinBit.order.dto.OrderDto;
import com.jun.coinBit.order.entity.*;
import com.jun.coinBit.balance.repository.BalanceRepository;
import com.jun.coinBit.order.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingScheduler {
    private final OrderRepository orderRepository;
    private final BalanceRepository balanceRepository;
    private final CoinService coinService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String KRW = "KRW";

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void matchOpenOrders() {
        List<Order> openOrders = orderRepository.findByStatus(OrderStatus.OPEN);
        for (Order order : openOrders) {
            String market = order.getCoin().getMarket();
            coinService.getCurrentPrice(market).ifPresent(currentPrice -> {
                if (shouldExecute(order, currentPrice)) {
                    executeOrder(order, currentPrice);
                }
            });
        }
    }

    // 지정가 조건 체크
    private boolean shouldExecute(Order order, BigDecimal currentPrice) {
        return switch (order.getSide()) {
            case BUY -> currentPrice.compareTo(order.getPrice()) <= 0;  // 가격이 지정가 이하
            case SELL -> currentPrice.compareTo(order.getPrice()) >= 0; // 가격이 지정가 이상
        };
    }

    private void executeOrder(Order order, BigDecimal execPrice) {
        BigDecimal total = execPrice.multiply(order.getVolume());
        User user = order.getUser();
        Coin coin = order.getCoin();
        Coin krw = coinService.findCoinOrThrow(KRW);

        if (order.getSide() == OrderSide.BUY) {
            Balance krwBalance = balanceRepository.findByUserAndCoin(user, krw)
                    .orElseThrow(() -> new RuntimeException("KRW 잔고 없음"));
            if (krwBalance.getAmount().compareTo(total) < 0) {
                log.warn("잔고 부족으로 주문 체결 실패: {}", order.getId());
                return;
            }
            krwBalance.decrease(total);
            Balance coinBalance = getOrCreateBalance(user, coin);
            coinBalance.increase(order.getVolume());

            balanceRepository.save(krwBalance);
            balanceRepository.save(coinBalance);
        } else if (order.getSide() == OrderSide.SELL) {
            Balance coinBalance = balanceRepository.findByUserAndCoin(user, coin)
                    .orElseThrow(() -> new RuntimeException("코인 잔고 없음"));
            if (coinBalance.getAmount().compareTo(order.getVolume()) < 0) {
                log.warn("보유 수량 부족으로 주문 체결 실패: {}", order.getId());
                return;
            }
            coinBalance.decrease(order.getVolume());
            Balance krwBalance = getOrCreateBalance(user, krw);
            krwBalance.increase(total);

            balanceRepository.save(coinBalance);
            balanceRepository.save(krwBalance);
        }

        // 주문 상태 업데이트
        order.Filled(execPrice);
        orderRepository.save(order);

        log.info("✅ 주문 체결 완료: id={}, user={}, market={}, side={}, price={}",
                order.getId(), user.getId(), coin.getMarket(), order.getSide(), execPrice);

        // ✅ WebSocket 체결 알림 전송
        String destination = "/topic/orders/user/" + user.getId();
        OrderDto orderDto = OrderDto.from(order);

        log.info("📦 체결 알림 전송: {}", orderDto);
        messagingTemplate.convertAndSend(destination, orderDto);
    }

    // 유저가 해당 코인 잔고 없으면 생성
    private Balance getOrCreateBalance(User user, Coin coin) {
        return balanceRepository.findByUserAndCoin(user, coin)
                .orElseGet(() -> new Balance(
                        new BalanceId(user.getId(), coin.getMarket()), user, coin, BigDecimal.ZERO));
    }
}