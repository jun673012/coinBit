package com.jun.coinBit.leaderboard.service;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.auth.repository.UserRepository;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.balance.repository.BalanceRepository;
import com.jun.coinBit.coin.service.CoinService;
import com.jun.coinBit.leaderboard.dto.LeaderboardDto;
import com.jun.coinBit.order.entity.Order;
import com.jun.coinBit.order.entity.OrderSide;
import com.jun.coinBit.order.entity.OrderStatus;
import com.jun.coinBit.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final UserRepository userRepository;
    private final BalanceRepository balanceRepository;
    private final OrderRepository orderRepository;
    private final CoinService coinService;

    public List<LeaderboardDto> getLeaderboard(String period, String search) {
        List<User> users = userRepository.findAll();
        List<LeaderboardDto> leaderboard = new ArrayList<>();

        for (User user : users) {
            // 포트폴리오 서비스와 동일한 방식으로 잔고 조회
            List<Balance> balances = balanceRepository.findByUser_Id(user.getId());
            // 현금 KRW 잔액 분리 (포트폴리오 서비스와 동일한 로직)
            BigDecimal krw = balances.stream()
                    .filter(b -> "KRW".equals(b.getCoin().getMarket()))
                    .map(Balance::getAmount)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            // 코인 잔고 Map으로 정리 (KRW 제외, 포트폴리오 서비스와 동일)
            Map<String, Balance> coinBalanceMap = new HashMap<>();
            for (Balance b : balances) {
                String market = b.getCoin().getMarket();
                if (!"KRW".equals(market) && b.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                    coinBalanceMap.put(market, b);
                    log.info("Coin balance - Market: {}, Amount: {}", market, b.getAmount());
                }
            }

            BigDecimal totalEval = BigDecimal.ZERO;  // KRW 제외, 코인 평가액만
            BigDecimal totalInvestment = BigDecimal.ZERO;

            // 포트폴리오 서비스와 완전히 동일한 계산 로직
            for (Map.Entry<String, Balance> entry : coinBalanceMap.entrySet()) {
                String market = entry.getKey();
                Balance balance = entry.getValue();
                BigDecimal volume = balance.getAmount();

                log.info("Processing coin: {}", market);

                // 체결된 주문 필터링 (포트폴리오 서비스와 동일)
                List<Order> orders = orderRepository.findByUser_IdAndCoin_MarketAndStatus(
                        user.getId(), market, OrderStatus.FILLED);

                log.info("Orders count for {}: {}", market, orders.size());

                BigDecimal buyTotal = BigDecimal.ZERO;
                BigDecimal sellTotal = BigDecimal.ZERO;
                for (Order o : orders) {
                    BigDecimal amount = Optional.ofNullable(o.getPrice()).orElse(BigDecimal.ZERO)
                            .multiply(Optional.ofNullable(o.getVolume()).orElse(BigDecimal.ZERO));
                    if (o.getSide() == OrderSide.BUY) {
                        buyTotal = buyTotal.add(amount);
                        log.info("BUY order: price={}, volume={}, amount={}", o.getPrice(), o.getVolume(), amount);
                    } else if (o.getSide() == OrderSide.SELL) {
                        sellTotal = sellTotal.add(amount);
                        log.info("SELL order: price={}, volume={}, amount={}", o.getPrice(), o.getVolume(), amount);
                    }
                }
                BigDecimal coinInvestment = buyTotal.subtract(sellTotal);
                log.info("{} - BuyTotal: {}, SellTotal: {}, CoinInvestment: {}", market, buyTotal, sellTotal, coinInvestment);

                // 투자원금이 음수면 0으로 처리 (포트폴리오 서비스와 동일)
                if (coinInvestment.compareTo(BigDecimal.ZERO) < 0) {
                    coinInvestment = BigDecimal.ZERO;
                    log.info("{} - Investment was negative, set to 0", market);
                }

                BigDecimal currentPrice = coinService.getCurrentPrice(market).orElse(BigDecimal.ZERO);
                BigDecimal evalAmount = volume.multiply(currentPrice);

                log.info("{} - Volume: {}, CurrentPrice: {}, EvalAmount: {}", market, volume, currentPrice, evalAmount);

                // 전체 집계
                totalEval = totalEval.add(evalAmount);
                totalInvestment = totalInvestment.add(coinInvestment);

                log.info("{} - Running totals - TotalEval: {}, TotalInvestment: {}", market, totalEval, totalInvestment);
            }

            // 전체 수익률 계산 (포트폴리오 서비스와 동일)
            BigDecimal overallProfitPercent = BigDecimal.ZERO;
            if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalProfit = totalEval.subtract(totalInvestment);
                overallProfitPercent = totalProfit.divide(totalInvestment, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            log.info("FINAL - TotalEval: {}, TotalInvestment: {}, ProfitPercent: {}",
                    totalEval, totalInvestment, overallProfitPercent);

            // 검색 필터
            if (matchesSearch(user, search)) {
                leaderboard.add(new LeaderboardDto(
                        user.getId(),
                        user.getNickname(),
                        totalEval.add(krw).setScale(0, RoundingMode.DOWN), // 총자산 = 코인평가액 + 현금
                        overallProfitPercent.setScale(2, RoundingMode.HALF_UP)
                ));
            }
        }

        // 총자산 기준 정렬
        return leaderboard.stream()
                .sorted(Comparator.comparing(LeaderboardDto::totalAsset).reversed())
                .toList();
    }

    private boolean matchesSearch(User user, String search) {
        if (search == null || search.isBlank()) return true;
        if (user.getNickname() == null) return false;
        return user.getNickname().toLowerCase().contains(search.toLowerCase());
    }
}