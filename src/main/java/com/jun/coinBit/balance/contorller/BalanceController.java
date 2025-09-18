package com.jun.coinBit.balance.contorller;


import com.jun.coinBit.balance.dto.BalanceDto;
import com.jun.coinBit.balance.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping("/balance")
    public ResponseEntity<BalanceDto> balance(@RequestParam Long userId) {
        return ResponseEntity.ok(balanceService.getBalance(userId));
    }
}
