package com.jun.coinBit.order.repository;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.order.entity.Order;
import com.jun.coinBit.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_Id(Long userId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByUser_IdAndCoin_MarketAndStatus(Long userId, String market, OrderStatus status);
}
