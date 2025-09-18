package com.jun.coinBit.coin.controller;

import com.jun.coinBit.coin.dto.CandleDto;
import com.jun.coinBit.coin.dto.CoinListDto;
import com.jun.coinBit.coin.dto.CoinPriceDto;
import com.jun.coinBit.coin.dto.TradeDto;
import com.jun.coinBit.coin.service.CoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class CoinController {
    private final CoinService coinService;

    @GetMapping("/coins")
    public ResponseEntity<CoinListDto> getCoinList() {
        try {
            CoinListDto coinList = coinService.getCoinList();
            return ResponseEntity.ok(coinList);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/candles")
    public ResponseEntity<List<CandleDto>> getCandles(
            @RequestParam String market,
            @RequestParam(defaultValue = "1") int unit
    ) {
        try {
            List<CandleDto> candles = coinService.getCandles(market, unit);
            return ResponseEntity.ok(candles);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeDto>> getTrades(@RequestParam String market, @RequestParam(defaultValue = "200") int count) {
        try {
            List<TradeDto> trades = coinService.getTrades(market, count);
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/current-prices/all")
    public ResponseEntity<Map<String, CoinPriceDto>> getAllCurrentPrices() {
        return ResponseEntity.ok(coinService.getAllCurrentPrices());
    }
}