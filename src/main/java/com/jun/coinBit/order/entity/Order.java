package com.jun.coinBit.order.entity;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.coin.entity.Coin;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market", referencedColumnName = "market")
    private Coin coin;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    private BigDecimal volume;
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private OrderType ordType;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt = LocalDateTime.now();

    public void Filled(BigDecimal execPrice) {
        this.status = OrderStatus.FILLED;
        this.price = execPrice;
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
    }
}