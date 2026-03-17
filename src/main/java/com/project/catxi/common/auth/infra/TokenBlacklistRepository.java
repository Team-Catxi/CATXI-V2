package com.project.catxi.common.auth.infra;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TokenBlacklistRepository {

    private final RedisTemplate<String, String> tokenRedisTemplate;

    public TokenBlacklistRepository(@Qualifier("tokenRedisTemplate") RedisTemplate<String, String> tokenRedisTemplate) {
        this.tokenRedisTemplate = tokenRedisTemplate;
    }

    private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";
    private static final String USER_BLACKLIST_PREFIX = "blacklist:user:";
    
    // AccessToken을 블랙리스트에 추가 (TTL = 토큰 남은 유효기간)
    public void addTokenToBlacklist(String accessToken, Duration ttl) {
        tokenRedisTemplate.opsForValue().set(TOKEN_BLACKLIST_PREFIX + accessToken, "true", ttl);
    }
    
    // AccessToken 블랙리스트 존재 조회
    public boolean isTokenBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(tokenRedisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + accessToken));
    }
    
    // User를 블랙리스트에 추가 (영구 또는 TTL 설정)
    public void addUserToBlacklist(String userId) {
        tokenRedisTemplate.opsForValue().set(USER_BLACKLIST_PREFIX + userId, "banned");
    }
    
    // User 블랙리스트 존재 조회
    public boolean isUserBlacklisted(String userId) {
        return Boolean.TRUE.equals(tokenRedisTemplate.hasKey(USER_BLACKLIST_PREFIX + userId));
    }
    
    // User 블랙리스트에서 제거
    public void removeUserFromBlacklist(String userId) {
        tokenRedisTemplate.delete(USER_BLACKLIST_PREFIX + userId);
    }
}