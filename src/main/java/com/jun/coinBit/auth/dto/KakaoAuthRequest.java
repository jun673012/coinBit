package com.jun.coinBit.auth.dto;

public record KakaoAuthRequest(
        String code,
        String nickname
) {}
