package com.jun.coinBit.coin.dto;

public record OrderbookUnitDto(
        double askPrice,
        double askSize,
        double bidPrice,
        double bidSize
) {}
