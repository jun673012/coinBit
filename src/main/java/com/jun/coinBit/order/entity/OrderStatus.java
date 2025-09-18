package com.jun.coinBit.order.entity;

public enum OrderStatus {
    OPEN, FILLED, CANCELLED;

    public String getKrName() {
        return switch (this) {
            case OPEN -> "대기";
            case FILLED -> "체결";
            case CANCELLED -> "취소";
        };
    }
}