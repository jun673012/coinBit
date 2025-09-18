package com.jun.coinBit.coin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jun.coinBit.coin.entity.Coin;
import com.jun.coinBit.coin.repository.CoinRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoinScheduler {
    private final CoinRepository coinRepository;
    private final ObjectMapper objectMapper;
    private final OkHttpClient client;

    @Scheduled(cron = "* * * * * *")
    @Transactional
    public void updateMarketCodes() {
        String url = "https://api.upbit.com/v1/market/all";
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("accept", "application/json")
                    .build();

            Response response = client.newCall(request).execute();

            if (!response.isSuccessful()) {
                log.warn("업비트 마켓 코드 조회 실패: HTTP {}", response.code());
                return;
            }

            String responseBody = response.body() != null ? response.body().string() : null;

            if (responseBody == null || responseBody.isEmpty()) {
                log.warn("업비트 마켓 코드 조회 결과가 비어있습니다.");
                return;
            }

            JsonNode arr = objectMapper.readTree(responseBody);
            List<Coin> codes = new ArrayList<>();
            for (JsonNode node : arr) {
                String market = node.hasNonNull("market") ? node.get("market").asText() : null;
                String koreanName = node.hasNonNull("korean_name") ? node.get("korean_name").asText() : null;
                String englishName = node.hasNonNull("english_name") ? node.get("english_name").asText() : null;
                if (market != null && market.startsWith("KRW-")) {
                    codes.add(new Coin(market, koreanName, englishName));
                }
            }

            // 🔑 현금 잔고 처리를 위한 KRW 엔트리 추가
            boolean hasKRW = coinRepository.existsById("KRW");
            if (!hasKRW) {
                codes.add(new Coin("KRW", "원화", "Korean Won"));
            }

            if (!codes.isEmpty()) {
                coinRepository.saveAll(codes);
            }
        } catch (Exception e) {
            log.error("마켓 코드 업데이트 실패", e);
        }
    }

}
