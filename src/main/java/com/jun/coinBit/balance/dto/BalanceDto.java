package com.jun.coinBit.balance.dto;

import java.math.BigDecimal;
import java.util.Map;

public record BalanceDto(
        String userId,
        Map<String, BigDecimal> coinBalances,
        BigDecimal cash
) {}