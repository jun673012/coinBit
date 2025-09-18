package com.jun.coinBit.order.controller;

import com.jun.coinBit.order.dto.BuySellRequest;
import com.jun.coinBit.order.dto.OrderDto;
import com.jun.coinBit.order.entity.Order;
import com.jun.coinBit.order.entity.OrderType;
import com.jun.coinBit.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trade")
@RequiredArgsConstructor
public class TradeController {
    private final OrderService tradeService;

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestBody BuySellRequest req) {
        try {
            validateRequest(req);

            // ✅ null 검사 + 유효값 검사
            if (req.ordType() == null) {
                return ResponseEntity.badRequest().body("ordType(주문 타입)이 누락되었습니다.");
            }

            OrderType orderType;
            try {
                orderType = OrderType.valueOf(req.ordType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("ordType 값이 올바르지 않습니다. 'LIMIT' 또는 'MARKET'만 허용됩니다.");
            }

            // ✅ 정상 흐름
            Order order = tradeService.buy(
                    req.userId(),
                    req.market(),
                    req.volume(),
                    req.price(),
                    orderType
            );
            return ResponseEntity.ok(OrderDto.from(order));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(@RequestBody BuySellRequest req) {
        try {
            validateRequest(req);

            if (req.ordType() == null) {
                return ResponseEntity.badRequest().body("ordType(주문 타입)이 누락되었습니다.");
            }

            OrderType orderType;
            try {
                orderType = OrderType.valueOf(req.ordType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body("ordType 값이 올바르지 않습니다. 'LIMIT' 또는 'MARKET'만 허용됩니다.");
            }

            Order order = tradeService.sell(
                    req.userId(),
                    req.market(),
                    req.volume(),
                    req.price(),
                    orderType
            );
            return ResponseEntity.ok(OrderDto.from(order));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<List<OrderDto>> orders(@RequestParam Long userId) {
        return ResponseEntity.ok(tradeService.getOrderHistory(userId));
    }

    @PatchMapping("/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long orderId, @RequestParam Long userId) {
        tradeService.cancelOrder(orderId, userId);

        return ResponseEntity.ok().build();
    }

    private void validateRequest(BuySellRequest req) {
        if (req == null
                || req.userId() == null
                || req.market() == null || req.market().isBlank()
                || req.volume() == null || req.volume().doubleValue() <= 0
        ) {
            throw new IllegalArgumentException("요청 값이 올바르지 않습니다.");
        }

        if ("LIMIT".equalsIgnoreCase(req.ordType())
                && (req.price() == null || req.price().doubleValue() <= 0)) {
            throw new IllegalArgumentException("지정가 주문은 가격이 필요합니다.");
        }
    }
}
