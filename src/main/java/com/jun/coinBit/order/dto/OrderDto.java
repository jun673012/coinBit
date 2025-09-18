package com.jun.coinBit.order.dto;

import com.jun.coinBit.order.entity.Order;

import java.time.format.DateTimeFormatter;

public record OrderDto(
        Long id,
        String userId,
        String market,
        String coinName,
        String side,
        String type,
        String status,
        double price,
        double volume,
        String timestamp
) {
    public static OrderDto from(Order order) {
        return new OrderDto(
                order.getId(),
                String.valueOf(order.getUser().getId()),
                order.getCoin().getMarket(),
                order.getCoin().getKoreanName(),
                order.getSide().getKrName(),
                order.getOrdType().getKrName(),
                order.getStatus().getKrName(),
                order.getPrice().doubleValue(),
                order.getVolume().doubleValue(),
                order.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }
}
