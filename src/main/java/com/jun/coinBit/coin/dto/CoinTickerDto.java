package com.jun.coinBit.coin.dto;

public record CoinTickerDto(
        String market,
        String koreanName,
        double tradePrice,
        double signedChangeRate,
        double accTradePrice24h
) {}

