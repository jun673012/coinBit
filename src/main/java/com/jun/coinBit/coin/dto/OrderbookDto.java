package com.jun.coinBit.coin.dto;

import java.util.List;

public record OrderbookDto(
        String market,
        List<OrderbookUnitDto> units
) {}