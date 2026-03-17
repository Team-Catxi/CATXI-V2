package com.project.catxi.common.auth.kakao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class KakaoUtil {

  @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
  private String client;
  @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
  private String redirect;

  // 인가 코드 -> accessToken 요청
  public KakaoDTO.kakaoToken requestToken(String accessCode) {
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();

    log.info(">> [카카오 토큰 요청 시작] time = {}, code = {}", LocalDateTime.now(), accessCode);
    log.info(">> [사용된 설정] client_id = {}, redirect_uri = {}", client, redirect);

    headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("client_id", client);
    params.add("redirect_uri", redirect);
    params.add("code", accessCode);

    HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

    try {
      ResponseEntity<String> response = restTemplate.exchange(
              "https://kauth.kakao.com/oauth/token",
              HttpMethod.POST,
              kakaoTokenRequest,
              String.class);

      log.info(">> [카카오 응답 성공] status = {}, body = {}",
              response.getStatusCode(), response.getBody());

      ObjectMapper objectMapper = new ObjectMapper();
      return objectMapper.readValue(response.getBody(), KakaoDTO.kakaoToken.class);

    } catch (Exception e) {
      log.error(">> [카카오 토큰 요청 실패] error = {}", e.getMessage());
      log.error(">> [요청 파라미터] params = {}", params);
      throw new IllegalArgumentException("카카오 토큰 요청 실패: " + e.getMessage());
    }
  }

  public KakaoDTO.KakaoProfile requestProfile(KakaoDTO.kakaoToken kakaoToken){
    RestTemplate restTemplate2 = new RestTemplate();
    HttpHeaders headers2 = new HttpHeaders();

    headers2.setBearerAuth(kakaoToken.access_token());

    HttpEntity<Void> kakaoProfileRequest = new HttpEntity<>(headers2);

    //GET 요청으로 프로필 받아오기 위함
    ResponseEntity<String> response2 = restTemplate2.exchange(
        "https://kapi.kakao.com/v2/user/me",
        HttpMethod.GET,
        kakaoProfileRequest,
        String.class);

    System.out.println("Kakao Profile Raw Response: " + response2.getBody());

    ObjectMapper objectMapper = new ObjectMapper();

    try {
      return objectMapper.readValue(response2.getBody(), KakaoDTO.KakaoProfile.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid profile token");
    }

  }

}
