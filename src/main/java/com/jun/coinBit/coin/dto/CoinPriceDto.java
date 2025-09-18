package com.jun.coinBit.coin.dto;

import java.math.BigDecimal;

public record CoinPriceDto(
        String koreanName,
        BigDecimal tradePrice
) {}