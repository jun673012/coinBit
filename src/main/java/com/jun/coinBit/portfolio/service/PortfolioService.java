package com.jun.coinBit.portfolio.service;

import com.jun.coinBit.coin.service.CoinService;
import com.jun.coinBit.portfolio.dto.PortfolioDto;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.portfolio.dto.PortfolioEntry;
import com.jun.coinBit.order.entity.Order;
import com.jun.coinBit.order.entity.OrderSide;
import com.jun.coinBit.order.entity.OrderStatus;
import com.jun.coinBit.balance.repository.BalanceRepository;
import com.jun.coinBit.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final BalanceRepository balanceRepository;
    private final OrderRepository orderRepository;
    private final CoinService coinService;

    public PortfolioDto getPortfolio(Long userId) {
        // 1. 현재 잔고 조회
        List<Balance> balances = balanceRepository.findByUser_Id(userId);

        // 2. 현금 KRW 잔액 분리
        BigDecimal krw = balances.stream()
                .filter(b -> "KRW".equals(b.getCoin().getMarket()))
                .map(Balance::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        // 3. 코인 잔고 Map으로 정리 (KRW 제외)
        Map<String, Balance> coinBalanceMap = new HashMap<>();
        for (Balance b : balances) {
            String market = b.getCoin().getMarket();
            if (!"KRW".equals(market) && b.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                coinBalanceMap.put(market, b);
            }
        }

        List<PortfolioEntry> entries = new ArrayList<>();
        BigDecimal totalEval = krw;
        BigDecimal totalInvestment = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        for (Map.Entry<String, Balance> entry : coinBalanceMap.entrySet()) {
            String market = entry.getKey();
            Balance balance = entry.getValue();
            BigDecimal volume = balance.getAmount();

            String coinSymbol = market.split("-")[1];
            String coinName = balance.getCoin().getKoreanName();

            // 체결된 주문 필터링
            List<Order> orders = orderRepository.findByUser_IdAndCoin_MarketAndStatus(
                    userId, market, OrderStatus.FILLED);

            BigDecimal buyTotal = BigDecimal.ZERO;
            BigDecimal sellTotal = BigDecimal.ZERO;
            for (Order o : orders) {
                BigDecimal amount = Optional.ofNullable(o.getPrice()).orElse(BigDecimal.ZERO)
                        .multiply(Optional.ofNullable(o.getVolume()).orElse(BigDecimal.ZERO));
                if (o.getSide() == OrderSide.BUY) {
                    buyTotal = buyTotal.add(amount);
                } else if (o.getSide() == OrderSide.SELL) {
                    sellTotal = sellTotal.add(amount);
                }
            }
            BigDecimal coinInvestment = buyTotal.subtract(sellTotal);

            // 투자원금이 음수면 0으로 처리 (예상치 못한 상황 대비)
            if (coinInvestment.compareTo(BigDecimal.ZERO) < 0) {
                coinInvestment = BigDecimal.ZERO;
            }

            BigDecimal currentPrice = coinService.getCurrentPrice(market).orElse(BigDecimal.ZERO);
            BigDecimal evalAmount = volume.multiply(currentPrice);

            BigDecimal profitAmount = evalAmount.subtract(coinInvestment);

            BigDecimal profitPercent = BigDecimal.ZERO;
            if (coinInvestment.compareTo(BigDecimal.ZERO) > 0) {
                profitPercent = profitAmount.divide(coinInvestment, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            // 전체 집계
            totalEval = totalEval.add(evalAmount);
            totalInvestment = totalInvestment.add(coinInvestment);
            totalProfit = totalProfit.add(profitAmount);

            entries.add(new PortfolioEntry(
                    coinSymbol,
                    coinName,
                    volume,
                    currentPrice,
                    evalAmount,
                    coinInvestment,
                    profitAmount,
                    profitPercent
            ));
        }

        // 전체 수익률 계산 (투자원금 0 이하면 0%)
        BigDecimal overallProfitPercent = BigDecimal.ZERO;
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            overallProfitPercent = totalProfit.divide(totalInvestment, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return new PortfolioDto(
                userId,
                totalEval,
                krw,
                entries,
                totalInvestment,
                totalProfit,
                overallProfitPercent
        );
    }
}
