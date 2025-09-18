// AuthController.java
package com.jun.coinBit.auth.controller;

import com.jun.coinBit.auth.dto.AuthResponse;
import com.jun.coinBit.auth.dto.KakaoAuthRequest;
import com.jun.coinBit.auth.provider.JwtTokenProvider;
import com.jun.coinBit.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/mock-login")
    public ResponseEntity<?> mockLogin(@RequestParam Long userId) {
        try {
            AuthResponse response = authService.mockLogin(userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Mock 로그인 중 오류 발생");
        }
    }

    // 로그인
    @GetMapping("/kakao/callback")
    public ResponseEntity<?> kakaoCallback(@RequestParam("code") String code) {
        try {
            AuthResponse response = authService.authenticateKakaoUser(code);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("신규 회원")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("가입되지 않은 사용자입니다. 닉네임 입력 후 회원가입을 진행하세요.");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("로그인 실패: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류");
        }
    }

    // 회원가입
    @PostMapping("/kakao/signup")
    public ResponseEntity<?> kakaoSignup(@RequestBody KakaoAuthRequest request) {
        try {
            AuthResponse response = authService.signupKakaoUser(request.code(), request.nickname());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("가입 처리 중 오류");
        }
    }

//    // 토큰 리프레시
//    @PostMapping("/refresh")
//    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String refreshToken) {
//        try {
//            String token = refreshToken.replace("Bearer ", "");
//            AuthResponse response = authService.refreshToken(token);
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body("토큰 갱신 실패: " + e.getMessage());
//        }
//    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        try {
            String jwtToken = token.replace("Bearer ", "");
            authService.logout(jwtToken);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("로그아웃 실패: " + e.getMessage());
        }
    }

//    // 현재 사용자 정보 조회
//    @GetMapping("/me")
//    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
//        try {
//            String jwtToken = token.replace("Bearer ", "");
//            if (authService.isTokenBlacklisted(jwtToken)) {
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                        .body("토큰이 만료되었거나 로그아웃된 사용자입니다.");
//            }
//
//            if (!jwtTokenProvider.isTokenValid(jwtToken)) {
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                        .body("유효하지 않은 토큰입니다.");
//            }
//
//            AuthResponse response = authService.getCurrentUser(jwtToken);
//            return ResponseEntity.ok(response);
//
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body("인증 정보 조회 실패: " + e.getMessage());
//        }
//    }

    // 회원 탈퇴
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestHeader("Authorization") String authorizationHeader) {
        try {
            String accessToken = authorizationHeader.replace("Bearer ", "");
            authService.deleteUser(accessToken);
            return ResponseEntity.ok("회원 탈퇴가 정상 처리되었습니다.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("회원 탈퇴 처리 중 오류가 발생했습니다.");
        }
    }
}
