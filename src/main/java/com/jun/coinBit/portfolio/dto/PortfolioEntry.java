package com.jun.coinBit.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioEntry(
        String coin,
        String coinName,
        BigDecimal volume,
        BigDecimal currentPrice,
        BigDecimal evalAmount,
        BigDecimal totalBuy,
        BigDecimal profitAmount,
        BigDecimal profitPercent
) {}
