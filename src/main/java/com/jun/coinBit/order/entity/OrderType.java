package com.jun.coinBit.order.entity;

public enum OrderType {
    LIMIT, MARKET;

    public String getKrName() {
        return switch (this) {
            case LIMIT -> "지정가";
            case MARKET -> "시장가";
        };
    }
}