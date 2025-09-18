package com.jun.coinBit.balance.service;


import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.auth.repository.UserRepository;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.balance.entity.BalanceId;
import com.jun.coinBit.balance.repository.BalanceRepository;
import com.jun.coinBit.coin.entity.Coin;
import com.jun.coinBit.coin.repository.CoinRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceAllUserResetScheduler {

    private final UserRepository userRepository;
    private final BalanceRepository balanceRepository;
    private final CoinRepository coinRepository;

    @Scheduled(cron = "0 0 0 * * MON")
    @Transactional
    public void resetAllUserBalances() {
        List<User> users = userRepository.findAll();
        Coin krw = coinRepository.findById("KRW").orElseThrow();

        for (User user : users) {
            // 기존 코인 잔고 모두 삭제(혹은 0으로 바꿈)
            balanceRepository.deleteByUserAndCoinNot(user, krw);

            // KRW 잔고 리셋
            Balance krwBalance = balanceRepository.findByUserAndCoin(user, krw)
                    .orElse(new Balance(new BalanceId(user.getId(), "KRW"), user, krw, BigDecimal.ZERO));
            krwBalance.resetBalance();
            balanceRepository.save(krwBalance);
        }
    }
}
