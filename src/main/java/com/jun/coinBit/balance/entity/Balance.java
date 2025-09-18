package com.jun.coinBit.balance.entity;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.coin.entity.Coin;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "balance")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Balance {
    @EmbeddedId
    private BalanceId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("coinMarket")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market", referencedColumnName = "market")
    private Coin coin;

    private BigDecimal amount;

    public void increase(BigDecimal value) {
        this.amount = this.amount.add(value);
    }
    public void decrease(BigDecimal value) {
        this.amount = this.amount.subtract(value);
    }

    public void resetBalance() {
        this.amount = BigDecimal.valueOf(10_000_000);
    }
}