package com.jun.coinBit.order.dto;

import java.math.BigDecimal;

public record BuySellRequest(
        Long userId,
        String market,
        BigDecimal price,
        BigDecimal volume,
        String ordType
) {}