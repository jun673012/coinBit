package com.jun.coinBit.order.entity;

public enum OrderSide {
    BUY, SELL;

    public String getKrName() {
        return switch (this) {
            case BUY -> "매수";
            case SELL -> "매도";
        };
    }
}