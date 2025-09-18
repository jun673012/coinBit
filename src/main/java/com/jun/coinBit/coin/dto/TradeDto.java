package com.jun.coinBit.coin.dto;

public record TradeDto(
        String market,
        double tradePrice,
        double tradeVolume,
        long tradeTimestamp,
        String askBid
) {}
