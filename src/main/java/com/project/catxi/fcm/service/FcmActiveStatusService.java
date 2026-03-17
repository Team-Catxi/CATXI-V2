package com.project.catxi.fcm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmActiveStatusService {

    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;
    
    private static final String ACTIVE_STATUS_KEY_PREFIX = "chat:active:room:%d:user:%d";
    private static final String MEMBER_CACHE_KEY_PREFIX = "member:email:%s";
    private static final int ACTIVE_STATUS_TTL_MINUTES = 2; // 2분 TTL
    private static final int MEMBER_CACHE_TTL_MINUTES = 30; // 멤버 캐시 30분 TTL

    /**
     * 사용자 활성 상태 업데이트
     *
     * @param email 사용자 이메일
     * @param roomId 채팅방 ID
     * @param isActive 활성 상태
     */
    public void updateUserActiveStatus(String email, Long roomId, boolean isActive) {
        try {
            Long memberId = getMemberIdFromCacheOrDb(email);
            String key = String.format(ACTIVE_STATUS_KEY_PREFIX, roomId, memberId);

            if (isActive) {
                // 활성 상태로 설정 (TTL 2분) - 타임스탬프 저장
                String value = String.valueOf(System.currentTimeMillis());
                redisTemplate.opsForValue().set(key, value, ACTIVE_STATUS_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("사용자 활성 상태 설정 - MemberId: {}, RoomId: {}", memberId, roomId);
            } else {
                // 비활성 상태로 설정 (비동기 삭제)
                redisTemplate.unlink(key);
                log.debug("사용자 비활성 상태 설정 - MemberId: {}, RoomId: {}", memberId, roomId);
            }

        } catch (CatxiException e) {
            log.error("사용자 정보 조회 실패 - Email: {}, RoomId: {}, Active: {}, Error: {}",
                    email, roomId, isActive, e.getMessage());
            // 사용자 정보 조회 실패 시 활성 상태 업데이트를 건너뛰기
        } catch (Exception e) {
            log.error("사용자 활성 상태 업데이트 실패 - Email: {}, RoomId: {}, Active: {}, Error: {}",
                    email, roomId, isActive, e.getMessage(), e);
            // 활성 상태 업데이트 실패가 다른 기능에 영향을 주지 않도록 예외를 던지지 않음
        }
    }

    /**
     * 사용자가 채팅방에서 활성 상태인지 확인
     *
     * @param memberId 멤버 ID
     * @param roomId 채팅방 ID
     * @return 활성 상태 여부
     */
    public boolean isUserActiveInRoom(Long memberId, Long roomId) {
        try {
            String key = String.format(ACTIVE_STATUS_KEY_PREFIX, roomId, memberId);
            String value = redisTemplate.opsForValue().get(key);

            if (value != null) {
                // timestamp 기반으로 더 정확한 활성 상태 판단
                try {
                    long timestamp = Long.parseLong(value);
                    long elapsedMinutes = (System.currentTimeMillis() - timestamp) / (1000 * 60);
                    return elapsedMinutes < ACTIVE_STATUS_TTL_MINUTES;
                } catch (NumberFormatException e) {
                    // 기존 "1" 값과의 호환성을 위해 존재하면 활성으로 처리
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("사용자 활성 상태 확인 실패 - MemberId: {}, RoomId: {}, Error: {}",
                    memberId, roomId, e.getMessage(), e);
            // 확인 실패 시 기본적으로 비활성으로 처리 (알림 발송)
            return false;
        }
    }

    /**
     * 이메일로 멤버 ID를 캐시 또는 DB에서 조회
     * 
     * @param email 사용자 이메일
     * @return 멤버 ID
     * @throws CatxiException 멤버가 존재하지 않을 경우
     */
    private Long getMemberIdFromCacheOrDb(String email) {
        String cacheKey = String.format(MEMBER_CACHE_KEY_PREFIX, email);
        
        try {
            // 1. 캐시에서 먼저 조회
            String cachedMemberId = redisTemplate.opsForValue().get(cacheKey);
            if (cachedMemberId != null) {
                log.debug("캐시에서 멤버 ID 조회 성공 - Email: {}, MemberId: {}", email, cachedMemberId);
                return Long.parseLong(cachedMemberId);
            }
            
            // 2. 캐시에 없으면 DB에서 조회
            Member member = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
            
            // 3. 조회 결과를 캐시에 저장 (30분 TTL)
            redisTemplate.opsForValue().set(cacheKey, member.getId().toString(), 
                    MEMBER_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            
            log.debug("DB에서 멤버 조회 및 캐시 저장 - Email: {}, MemberId: {}", email, member.getId());
            return member.getId();
            
        } catch (NumberFormatException e) {
            log.warn("캐시된 멤버 ID 파싱 실패 - Email: {}, CachedValue: {}", email, 
                    redisTemplate.opsForValue().get(cacheKey));
            // 파싱 실패시 캐시 삭제 후 DB에서 재조회
            redisTemplate.delete(cacheKey);
            Member member = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
            
            redisTemplate.opsForValue().set(cacheKey, member.getId().toString(), 
                    MEMBER_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return member.getId();
        }
    }
}