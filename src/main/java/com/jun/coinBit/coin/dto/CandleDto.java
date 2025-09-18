package com.jun.coinBit.coin.dto;

public record CandleDto(
        String market,
        long timestamp,
        double opening_price,
        double high_price,
        double low_price,
        double trade_price,
        double candle_acc_trade_volume
) {}