package com.jun.coinBit.auth.service;

import com.jun.coinBit.auth.provider.JwtTokenProvider;
import com.jun.coinBit.auth.dto.AuthResponse;
import com.jun.coinBit.auth.dto.KakaoUserInfo;
import com.jun.coinBit.auth.entity.User;
import com.jun.coinBit.auth.repository.UserRepository;
import com.jun.coinBit.balance.entity.Balance;
import com.jun.coinBit.balance.entity.BalanceId;
import com.jun.coinBit.balance.repository.BalanceRepository;
import com.jun.coinBit.coin.entity.Coin;
import com.jun.coinBit.coin.repository.CoinRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BalanceRepository balanceRepository;
    private final CoinRepository coinRepository;
    private final KakaoApiClient kakaoApiClient;
    private final JwtTokenProvider jwtTokenProvider;

    // 메모리 기반 토큰 저장소
    private final Map<Long, String> refreshTokenStore = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> refreshTokenExpirationStore = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> blacklistStore = new ConcurrentHashMap<>();

    public AuthResponse mockLogin(Long userId) {
        // 1. 해당 유저가 실제로 존재하는지 확인
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("해당 유저가 존재하지 않습니다."));

        // 2. JWT 및 Refresh Token 발급
        String jwtToken = jwtTokenProvider.generateToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        saveRefreshTokenToMemory(user.getId(), refreshToken);

        // 3. 기존 AuthResponse 그대로 반환
        return new AuthResponse(jwtToken, refreshToken, user.toDto());
    }

    public AuthResponse authenticateKakaoUser(String code) {
        String accessToken = kakaoApiClient.getAccessToken(code);
        KakaoUserInfo kakaoUserInfo = kakaoApiClient.getUserInfo(accessToken);

        Optional<User> userOpt = userRepository.findByKakaoId(kakaoUserInfo.id());
        if (userOpt.isEmpty()) {
            throw new RuntimeException("신규 회원입니다. 닉네임 입력 후 가입하세요.");
        }

        User user = userOpt.get();

        String jwtToken = jwtTokenProvider.generateToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        saveRefreshTokenToMemory(user.getId(), refreshToken);

        return new AuthResponse(jwtToken, refreshToken, user.toDto());
    }

    /** 신규 회원 가입 (닉네임 필수) */
    public AuthResponse signupKakaoUser(String code, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new RuntimeException("신규 회원가입 시 닉네임을 입력해야 합니다.");
        }

        String accessToken = kakaoApiClient.getAccessToken(code);
        KakaoUserInfo kakaoUserInfo = kakaoApiClient.getUserInfo(accessToken);

        if (userRepository.findByKakaoId(kakaoUserInfo.id()).isPresent()) {
            throw new RuntimeException("이미 가입된 사용자입니다.");
        }

        User user = User.builder()
                .kakaoId(kakaoUserInfo.id())
                .nickname(nickname)
                .profileImage(kakaoUserInfo.profileImage())
                .build();

        user = userRepository.save(user);

        String jwtToken = jwtTokenProvider.generateToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        saveRefreshTokenToMemory(user.getId(), refreshToken);

        Coin krwCoin = coinRepository.findById("KRW").orElseThrow();
        Balance krwBalance = new Balance(new BalanceId(user.getId(), "KRW"), user, krwCoin, BigDecimal.valueOf(10_000_000));
        balanceRepository.save(krwBalance);

        return new AuthResponse(jwtToken, refreshToken, user.toDto());
    }

    /**
     * Refresh Token으로 새 토큰 발급
     */
    public AuthResponse refreshToken(String refreshToken) {
        try {
            // 1. Refresh Token 검증
            if (!jwtTokenProvider.isTokenValid(refreshToken)) {
                throw new RuntimeException("Invalid refresh token");
            }

            // 2. 사용자 ID 추출
            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

            // 3. 메모리에서 Refresh Token 확인
            String storedRefreshToken = getRefreshTokenFromMemory(userId);
            if (!refreshToken.equals(storedRefreshToken)) {
                throw new RuntimeException("Refresh token not found or expired");
            }

            // 4. 사용자 조회
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // 5. 새 토큰 생성
            String newAccessToken = jwtTokenProvider.generateToken(userId);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

            // 6. 새 Refresh Token 메모리 저장
            saveRefreshTokenToMemory(userId, newRefreshToken);

            return new AuthResponse(newAccessToken, newRefreshToken, user.toDto());

        } catch (Exception e) {
            throw new RuntimeException("Token refresh failed: " + e.getMessage());
        }
    }

    /**
     * 로그아웃 처리
     */
    public void logout(String token) {
        try {
            // 1. 토큰 검증
            if (!jwtTokenProvider.isTokenValid(token)) {
                throw new RuntimeException("Invalid token");
            }

            // 2. 사용자 ID 추출
            Long userId = jwtTokenProvider.getUserIdFromToken(token);

            // 3. 메모리에서 Refresh Token 삭제
            deleteRefreshTokenFromMemory(userId);

            // 4. 액세스 토큰 블랙리스트 처리
            blacklistToken(token);

        } catch (Exception e) {
            throw new RuntimeException("Logout failed: " + e.getMessage());
        }
    }

    /**
     * 현재 사용자 정보 조회
     */
//    public AuthResponse getCurrentUser(String token) {
//        try {
//            // 1. 토큰이 블랙리스트에 있는지 확인
//            if (isTokenBlacklisted(token)) {
//                throw new RuntimeException("Token is blacklisted");
//            }
//
//            // 2. 토큰 검증
//            if (!jwtTokenProvider.isTokenValid(token)) {
//                throw new RuntimeException("Invalid token");
//            }
//
//            // 3. 사용자 ID 추출
//            Long userId = jwtTokenProvider.getUserIdFromToken(token);
//
//            // 4. 사용자 조회
//            User user = userRepository.findById(userId)
//                    .orElseThrow(() -> new RuntimeException("User not found"));
//
//            return new AuthResponse(null, null, user.toDto());
//
//        } catch (Exception e) {
//            throw new RuntimeException("Failed to get current user: " + e.getMessage());
//        }
//    }

    @Transactional
    public void deleteUser(String accessToken) {
        if (!jwtTokenProvider.isTokenValid(accessToken)) {
            throw new RuntimeException("유효하지 않은 토큰입니다.");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(accessToken);
        balanceRepository.deleteByUserId(userId);
        // 1) 토큰 블랙리스트 처리해서 로그아웃시키기 (optional)
        blacklistToken(accessToken);

        // 2) Refresh Token 메모리에서 삭제
        deleteRefreshTokenFromMemory(userId);

        // 3) DB에서 회원 정보 삭제
        userRepository.deleteById(userId);
    }

    /**
     * 메모리에 Refresh Token 저장
     */
    private void saveRefreshTokenToMemory(Long userId, String refreshToken) {
        // 만료된 토큰들 정리
        cleanupExpiredTokens();

        refreshTokenStore.put(userId, refreshToken);
        LocalDateTime expiration = LocalDateTime.now().plusDays(7); // 7일 후 만료
        refreshTokenExpirationStore.put(userId, expiration);
    }

    /**
     * 메모리에서 Refresh Token 조회
     */
    private String getRefreshTokenFromMemory(Long userId) {
        // 만료 확인
        LocalDateTime expiration = refreshTokenExpirationStore.get(userId);
        if (expiration != null && expiration.isBefore(LocalDateTime.now())) {
            // 만료된 토큰 삭제
            refreshTokenStore.remove(userId);
            refreshTokenExpirationStore.remove(userId);
            return null;
        }

        return refreshTokenStore.get(userId);
    }

    /**
     * 메모리에서 Refresh Token 삭제
     */
    private void deleteRefreshTokenFromMemory(Long userId) {
        refreshTokenStore.remove(userId);
        refreshTokenExpirationStore.remove(userId);
    }

    /**
     * 토큰 블랙리스트 처리
     */
    private void blacklistToken(String token) {
        // 만료된 블랙리스트 토큰들 정리
        cleanupExpiredBlacklist();

        LocalDateTime expiration = LocalDateTime.now().plusDays(1); // 1일 후 만료
        blacklistStore.put(token, expiration);
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     */
//    public boolean isTokenBlacklisted(String token) {
//        LocalDateTime expiration = blacklistStore.get(token);
//        if (expiration != null && expiration.isBefore(LocalDateTime.now())) {
//            // 만료된 블랙리스트 토큰 삭제
//            blacklistStore.remove(token);
//            return false;
//        }
//        return blacklistStore.containsKey(token);
//    }

    /**
     * 만료된 Refresh Token들 정리
     */
    private void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenExpirationStore.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(now)) {
                refreshTokenStore.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * 만료된 블랙리스트 토큰들 정리
     */
    private void cleanupExpiredBlacklist() {
        LocalDateTime now = LocalDateTime.now();
        blacklistStore.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}