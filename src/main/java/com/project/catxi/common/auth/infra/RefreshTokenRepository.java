package com.project.catxi.common.auth.infra;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository{

  private final RedisTemplate<String, String> tokenRedisTemplate;

  public RefreshTokenRepository(@Qualifier("tokenRedisTemplate") RedisTemplate<String, String> tokenRedisTemplate) {
    this.tokenRedisTemplate = tokenRedisTemplate;
  }

  private static final String PREFIX = "refresh:";

  //RefreshToken Redis 저장

  public void save(String key, String token, Duration ttl) {
    tokenRedisTemplate.opsForValue().set(PREFIX + key, token, ttl);
  }

  //RefreshToken 조회
  public Optional<String> findByKey(String key) {
    return Optional.ofNullable(tokenRedisTemplate.opsForValue().get(PREFIX + key));
  }

  //유효성 검증
  public boolean isValid(String key, String token) {
    return findByKey(key).map(saved -> saved.equals(token)).orElse(false);
  }

  // 토큰 삭제 (이메일 키로)
  public void delete(String key) {
    tokenRedisTemplate.delete(PREFIX + key);
  }

  // 토큰 삭제 (토큰 값으로)
  public void deleteByToken(String token) {
    // Redis에서 모든 키를 순회하여 해당 토큰을 찾아 삭제
    tokenRedisTemplate.keys(PREFIX + "*").forEach(key -> {
      String storedToken = tokenRedisTemplate.opsForValue().get(key);
      if (token.equals(storedToken)) {
        tokenRedisTemplate.delete(key);
      }
    });
  }

  // 토큰 회전 (기존 토큰 삭제 후 새 토큰 저장)
  public void rotate(String key, String oldToken, String newToken, Duration ttl) {
    // 기존 토큰 검증 후 새 토큰으로 교체
    if (isValid(key, oldToken)) {
      delete(key);
      save(key, newToken, ttl);
    }
  }

}
