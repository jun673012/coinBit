package com.jun.coinBit.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioDto(
        Long userId,
        BigDecimal totalAsset,
        BigDecimal krw,
        List<PortfolioEntry> entries,
        BigDecimal totalInvestment,
        BigDecimal profitAmount,
        BigDecimal profitPercent
) {}
