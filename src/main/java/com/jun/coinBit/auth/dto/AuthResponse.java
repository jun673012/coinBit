package com.jun.coinBit.auth.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserDto user
) {}
