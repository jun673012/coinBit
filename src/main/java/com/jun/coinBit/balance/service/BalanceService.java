package com.jun.coinBit.balance.service;

import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.auth.repository.UserRepository;
import com.jun.coinBit.balance.dto.BalanceDto;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.balance.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final UserRepository userRepository;
    private final BalanceRepository balanceRepository;

    private static final String KRW = "KRW";

    public BalanceDto getBalance(Long userId) {
        User user = findUserOrThrow(userId);
        List<Balance> balances = balanceRepository.findByUser(user);

        Map<String, BigDecimal> coinBalances = new HashMap<>();
        BigDecimal cash = BigDecimal.ZERO;

        for (Balance balance : balances) {
            String market = balance.getCoin().getMarket();
            if (KRW.equals(market)) {
                cash = balance.getAmount();
            } else {
                coinBalances.put(market, balance.getAmount());
            }
        }
        return new BalanceDto(String.valueOf(userId), coinBalances, cash);
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));
    }
}
