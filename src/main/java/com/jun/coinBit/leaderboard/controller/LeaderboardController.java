package com.jun.coinBit.leaderboard.controller;

import com.jun.coinBit.leaderboard.dto.LeaderboardDto;
import com.jun.coinBit.leaderboard.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public List<LeaderboardDto> getLeaderboard(@RequestParam String period, @RequestParam(required = false) String search) {
        return leaderboardService.getLeaderboard(period, search);
    }
}