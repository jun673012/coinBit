package com.jun.coinBit.leaderboard.dto;

import java.math.BigDecimal;

public record LeaderboardDto(
        Long userId,
        String nickname,
        BigDecimal totalAsset,
        BigDecimal profitPercent
) {}