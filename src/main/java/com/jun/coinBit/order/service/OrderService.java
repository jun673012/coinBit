package com.jun.coinBit.order.service;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.auth.repository.UserRepository;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.balance.repository.BalanceRepository;
import com.jun.coinBit.coin.entity.Coin;
import com.jun.coinBit.coin.repository.CoinRepository;
import com.jun.coinBit.coin.service.CoinService;
import com.jun.coinBit.order.dto.OrderDto;
import com.jun.coinBit.order.entity.*;
import com.jun.coinBit.order.repository.OrderRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final BalanceRepository balanceRepository;
    private final UserRepository userRepository;
    private final CoinRepository coinRepository;
    private final CoinService coinService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String KRW = "KRW";

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));
    }

    private Coin findCoinOrThrow(String market) {
        return coinRepository.findById(market)
                .orElseThrow(() -> new IllegalArgumentException("코인 없음: " + market));
    }

    private Balance getOrCreateBalance(User user, Coin coin) {
        return balanceRepository.findByUserAndCoin(user, coin)
                .orElseGet(() -> new Balance(
                        new com.jun.coinBit.balance.entity.BalanceId(user.getId(), coin.getMarket()), user, coin, BigDecimal.ZERO
                ));
    }

    private Order createOrder(User user, Coin coin, OrderSide side, BigDecimal volume, BigDecimal price, OrderType type, OrderStatus status) {
        return Order.builder()
                .user(user)
                .coin(coin)
                .side(side)
                .volume(volume)
                .price(price)
                .ordType(type)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public List<OrderDto> getOrderHistory(Long userId) {
        return orderRepository.findByUser_Id(userId)
                .stream().map(OrderDto::from).toList();
    }

    @Transactional
    public Order buy(Long userId, String market, BigDecimal volume, BigDecimal price, OrderType ordType) {
        User user = findUserOrThrow(userId);
        Coin coin = findCoinOrThrow(market);
        BigDecimal currentPrice = coinService.getCurrentPrice(market)
                .orElseThrow(() -> new RuntimeException("현재가 정보를 가져올 수 없습니다."));

        boolean executable = false;
        BigDecimal execPrice = price;

        if (ordType == OrderType.MARKET) {
            execPrice = currentPrice;
            executable = true;
        } else if (ordType == OrderType.LIMIT) {
            if (currentPrice.compareTo(price) <= 0) {
                executable = true;
            }
        }

        OrderStatus orderStatus = executable ? OrderStatus.FILLED : OrderStatus.OPEN;
        BigDecimal totalPrice = execPrice.multiply(volume);

        Coin krwCoin = findCoinOrThrow(KRW);
        Balance krwBalance = balanceRepository.findByUserAndCoin(user, krwCoin)
                .orElseThrow(() -> new RuntimeException("KRW 잔고 없음"));

        if (executable && krwBalance.getAmount().compareTo(totalPrice) < 0) {
            throw new RuntimeException("잔고 부족");
        }

        if (executable) {
            krwBalance.decrease(totalPrice);
            balanceRepository.save(krwBalance);

            Balance coinBalance = getOrCreateBalance(user, coin);
            coinBalance.increase(volume);
            balanceRepository.save(coinBalance);
        }

        Order order = createOrder(user, coin, OrderSide.BUY, volume, execPrice, ordType, orderStatus);
        orderRepository.save(order);

        log.info("[주문 완료] orderId={}, userId={}, status={}", order.getId(), userId, order.getStatus());

        if (order.getStatus() == OrderStatus.FILLED) {
            OrderDto dto = OrderDto.from(order);
            messagingTemplate.convertAndSend("/topic/orders/user/" + userId, dto);
        }

        return order;
    }

    @Transactional
    public Order sell(Long userId, String market, BigDecimal volume, BigDecimal price, OrderType ordType) {
        User user = findUserOrThrow(userId);
        Coin coin = findCoinOrThrow(market);
        BigDecimal currentPrice = coinService.getCurrentPrice(market)
                .orElseThrow(() -> new RuntimeException("현재가 정보를 가져올 수 없습니다."));

        boolean executable = false;
        BigDecimal execPrice = price;

        if (ordType == OrderType.MARKET) {
            execPrice = currentPrice;
            executable = true;
        } else if (ordType == OrderType.LIMIT) {
            if (currentPrice.compareTo(price) >= 0) {
                executable = true;
            }
        }

        OrderStatus orderStatus = executable ? OrderStatus.FILLED : OrderStatus.OPEN;
        BigDecimal totalPrice = execPrice.multiply(volume);

        Balance coinBalance = balanceRepository.findByUserAndCoin(user, coin)
                .orElseThrow(() -> new RuntimeException("코인 잔고 없음"));

        if (coinBalance.getAmount().compareTo(volume) < 0) {
            throw new RuntimeException("보유 수량 부족");
        }

        if (executable) {
            coinBalance.decrease(volume);
            balanceRepository.save(coinBalance);

            Coin krwCoin = findCoinOrThrow(KRW);
            Balance krwBalance = getOrCreateBalance(user, krwCoin);
            krwBalance.increase(totalPrice);
            balanceRepository.save(krwBalance);
        }

        Order order = createOrder(user, coin, OrderSide.SELL, volume, execPrice, ordType, orderStatus);
        orderRepository.save(order);

        if (order.getStatus() == OrderStatus.FILLED) {
            messagingTemplate.convertAndSend("/topic/orders/user/" + userId, OrderDto.from(order));
        }

        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음"));

        if (!order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new IllegalStateException("이미 체결되었거나 취소된 주문입니다.");
        }
        order.cancel();
        orderRepository.save(order);
    }
}
