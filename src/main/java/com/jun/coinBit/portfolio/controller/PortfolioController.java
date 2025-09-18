package com.jun.coinBit.portfolio.controller;

import com.jun.coinBit.portfolio.dto.PortfolioDto;
import com.jun.coinBit.portfolio.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    @GetMapping
    public PortfolioDto getPortfolio(@RequestParam Long userId) {
        return portfolioService.getPortfolio(userId);
    }
}
