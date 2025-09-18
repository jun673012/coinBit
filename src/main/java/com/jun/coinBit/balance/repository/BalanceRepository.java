package com.jun.coinBit.balance.repository;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.coin.entity.Coin;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.balance.entity.BalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BalanceRepository extends JpaRepository<Balance, BalanceId> {
    Optional<Balance> findByUserAndCoin(User user, Coin coin);

    List<Balance> findByUser(User user);

    List<Balance> findByUser_Id(Long userId);

    void deleteByUserId(Long userId);

    void deleteByUserAndCoinNot(User user, Coin coin);
}
