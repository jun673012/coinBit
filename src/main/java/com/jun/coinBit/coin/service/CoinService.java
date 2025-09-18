package com.jun.coinBit.coin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jun.coinBit.coin.dto.*;
import com.jun.coinBit.coin.entity.Coin;
import com.jun.coinBit.coin.repository.CoinRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Service
public class CoinService {
    private static final String UPBIT_WS_URL = "wss://api.upbit.com/websocket/v1";
    private static final int TRADE_HISTORY_MAX_SIZE = 50;
    private static final int CANDLE_COUNT = 200;

    private final CoinRepository coinRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final OkHttpClient okHttpClient;
    private final CoinScheduler coinScheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> marketToKoreanName = new HashMap<>();
    private final Map<String, Deque<TradeDto>> tradeHistoryMap = new HashMap<>();
    private final Map<String, Double> currentPriceMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        List<String> markets = new ArrayList<>();
        for (Coin coin : coinRepository.findAll()) {
            markets.add(coin.getMarket());
            marketToKoreanName.put(coin.getMarket(), coin.getKoreanName());
        }
        if (!markets.isEmpty()) {
            connectWebSocket(markets.stream().filter(m -> !"KRW".equals(m)).toList());
        }
    }

    @PostConstruct
    public void ensureKRWCoinExists() {
        if (!coinRepository.existsById("KRW")) {
            coinRepository.save(new Coin("KRW", "원화", "Korean Won"));
        }
    }

    public CoinListDto getCoinList() {
        List<String> markets = coinRepository.findAll()
                .stream()
                .map(Coin::getMarket)
                .toList();
        return new CoinListDto(markets);
    }

    public List<CandleDto> getCandles(String market, int unit) {
        try {
            String url = String.format(
                    "https://api.upbit.com/v1/candles/minutes/%d?market=%s&count=%d",
                    unit, market, CANDLE_COUNT
            );

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("accept", "application/json")
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Error fetching candles: HTTP " + response.code());
                    return List.of();
                }

                String json = response.body().string();
                JsonNode arr = objectMapper.readTree(json);
                List<CandleDto> result = new ArrayList<>();
                for (JsonNode node : arr) {
                    result.add(new CandleDto(
                            market,
                            node.get("timestamp").asLong(),
                            node.get("opening_price").asDouble(),
                            node.get("high_price").asDouble(),
                            node.get("low_price").asDouble(),
                            node.get("trade_price").asDouble(),
                            node.get("candle_acc_trade_volume").asDouble()
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            System.err.println("Error fetching candles: " + e.getMessage());
            return List.of();
        }
    }

    public List<TradeDto> getTrades(String market, int count) {
        try {
            String url = String.format(
                    "https://api.upbit.com/v1/trades/ticks?market=%s&count=%d",
                    market, count
            );

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("accept", "application/json")
                    .build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Error fetching trades: HTTP " + response.code());
                    return List.of();
                }

                String json = response.body().string();

                // 응답이 배열인지, 혹은 에러 메시지인지 확인
                JsonNode arr = objectMapper.readTree(json);
                if (!arr.isArray()) {
                    System.err.println("Unexpected response format for trades: " + json);
                    return List.of();
                }

                List<TradeDto> result = new ArrayList<>();
                for (JsonNode node : arr) {
                    JsonNode priceNode = node.get("trade_price");
                    JsonNode volumeNode = node.get("trade_volume");
                    JsonNode timestampNode = node.get("timestamp");  // 수정됨
                    JsonNode askBidNode = node.get("ask_bid");

                    if (priceNode == null || volumeNode == null || timestampNode == null || askBidNode == null) {
                        System.err.println("Skipping trade due to missing fields: " + node.toString());
                        continue;
                    }

                    result.add(new TradeDto(
                            market,
                            priceNode.asDouble(),
                            volumeNode.asDouble(),
                            timestampNode.asLong(),
                            askBidNode.asText()
                    ));
                }

                return result;
            }
        } catch (Exception e) {
            System.err.println("Error fetching trades: " + e.getMessage());
            return List.of();
        }
    }

    public Map<String, CoinPriceDto> getAllCurrentPrices() {
        Map<String, CoinPriceDto> result = new HashMap<>();
        for (String market : currentPriceMap.keySet()) {
            String koreanName = marketToKoreanName.getOrDefault(market, "-");
            Double price = currentPriceMap.get(market);
            if (price != null) {
                result.put(market, new CoinPriceDto(koreanName, BigDecimal.valueOf(price)));
            }
        }
        return result;
    }


    private void connectWebSocket(List<String> markets) {
        try {
            WebSocketClient client = new WebSocketClient(new URI(UPBIT_WS_URL)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    List<Object> subscribeMsg = List.of(
                            Map.of("ticket", UUID.randomUUID().toString()),
                            Map.of("type", "ticker", "codes", markets),
                            Map.of("type", "orderbook", "codes", markets),
                            Map.of("type", "trade", "codes", markets)
                    );
                    try {
                        send(objectMapper.writeValueAsString(subscribeMsg));
                    } catch (Exception e) {
                        System.err.println("Error sending subscription message: " + e.getMessage());
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    try {
                        String json = StandardCharsets.UTF_8.decode(bytes).toString();
                        JsonNode node = objectMapper.readTree(json);
                        String type = node.get("type").asText();

                        switch (type) {
                            case "ticker" -> handleTickerMessage(node);
                            case "orderbook" -> handleOrderbookMessage(node);
                            case "trade" -> handleTradeMessage(node);
                        }
                    } catch (Exception e) {
                        System.err.println("WebSocket message parsing error: " + e.getMessage());
                    }
                }

                private void handleTickerMessage(JsonNode node) {
                    String code = node.get("code").asText();
                    double price = node.get("trade_price").asDouble();

                    // 실시간 시세 저장
                    currentPriceMap.put(code, price);

                    CoinTickerDto dto = new CoinTickerDto(
                            code,
                            marketToKoreanName.getOrDefault(code, "-"),
                            price,
                            node.get("signed_change_rate").asDouble(),
                            node.get("acc_trade_price_24h").asDouble()
                    );

                    messagingTemplate.convertAndSend("/topic/ticker/" + code, dto);
                }

                private void handleOrderbookMessage(JsonNode node) {
                    String code = node.get("code").asText();
                    JsonNode units = node.get("orderbook_units");
                    List<OrderbookUnitDto> orderbook = new ArrayList<>();
                    for (JsonNode unit : units) {
                        orderbook.add(new OrderbookUnitDto(
                                unit.get("ask_price").asDouble(),
                                unit.get("ask_size").asDouble(),
                                unit.get("bid_price").asDouble(),
                                unit.get("bid_size").asDouble()
                        ));
                    }
                    messagingTemplate.convertAndSend("/topic/orderbook/" + code, new OrderbookDto(code, orderbook));
                }

                private void handleTradeMessage(JsonNode node) {
                    String code = node.get("code").asText();
                    TradeDto trade = new TradeDto(
                            code,
                            node.get("trade_price").asDouble(),
                            node.get("trade_volume").asDouble(),
                            node.get("trade_timestamp").asLong(),
                            node.get("ask_bid").asText()
                    );

                    tradeHistoryMap.computeIfAbsent(code, k -> new ArrayDeque<>());
                    Deque<TradeDto> history = tradeHistoryMap.get(code);
                    if (history.size() >= TRADE_HISTORY_MAX_SIZE) {
                        history.removeLast();
                    }
                    history.addFirst(trade);

                    messagingTemplate.convertAndSend("/topic/trade/" + code, new ArrayList<>(history));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.err.println("WebSocket Closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("WebSocket Error: " + ex.getMessage());
                }

                @Override
                public void onMessage(String message) {
                    // unused
                }
            };
            client.connect();
        } catch (Exception e) {
            System.err.println("WebSocket connection failed: " + e.getMessage());
        }
    }

    // 외부에서 현재가 가져오는 메소드
    public Optional<BigDecimal> getCurrentPrice(String market) {
        Double price = currentPriceMap.get(market);
        if (price == null) return Optional.empty();
        return Optional.of(BigDecimal.valueOf(price));
    }

    public Coin findCoinOrThrow(String market) {
        return coinRepository.findById(market)
                .orElseThrow(() -> new IllegalArgumentException("코인 없음: " + market));
    }

}