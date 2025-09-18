package com.jun.coinBit.auth.dto;

public record UserDto(
        Long id,
        String nickname,
        String profileImage
) {}