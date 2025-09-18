package com.jun.coinBit.coin.repository;

import com.jun.coinBit.coin.entity.Coin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CoinRepository extends JpaRepository<Coin, String> {
}
