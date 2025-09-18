package com.jun.coinBit.auth.service;

import com.jun.coinBit.auth.dto.KakaoUserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class KakaoApiClient {

    private final RestTemplate restTemplate;
    private final String kakaoClientId;
    private final String kakaoClientSecret;
    private final String kakaoRedirectUri;

    public KakaoApiClient(RestTemplate restTemplate,
                          @Value("${kakao.client-id}") String kakaoClientId,
                          @Value("${kakao.client-secret}") String kakaoClientSecret,
                          @Value("${kakao.redirect-uri}") String kakaoRedirectUri) {
        this.restTemplate = restTemplate;
        this.kakaoClientId = kakaoClientId;
        this.kakaoClientSecret = kakaoClientSecret;
        this.kakaoRedirectUri = kakaoRedirectUri;
    }

    public String getAccessToken(String code) {
        String url = "https://kauth.kakao.com/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", kakaoClientId);
        params.add("client_secret", kakaoClientSecret);
        params.add("redirect_uri", kakaoRedirectUri);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Kakao token API response is null");
            }
            return (String) responseBody.get("access_token");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kakao access token: " + e.getMessage());
        }
    }

    public KakaoUserInfo getUserInfo(String accessToken) {
        String url = "https://kapi.kakao.com/v2/user/me";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Kakao user info response is null");
            }

            Long id = Long.valueOf(responseBody.get("id").toString());
            Map<String, Object> properties = (Map<String, Object>) responseBody.get("properties");

            String nickname = properties != null ? (String) properties.get("nickname") : null;
            String profileImage = properties != null ? (String) properties.get("profile_image") : null;

            return new KakaoUserInfo(id, nickname, profileImage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kakao user info: " + e.getMessage());
        }
    }
}
