package com.project.catxi.fcm.service;

import com.project.catxi.common.api.error.FcmErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.fcm.dto.FcmTokenUpdateReq;
import com.project.catxi.fcm.dto.FcmTokenUpdateRes;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FcmTokenService {

    private final MemberRepository memberRepository;

    @Transactional
    public FcmTokenUpdateRes updateFcmToken(Member member, FcmTokenUpdateReq request) {
        try {
            // 토큰 유효성 검증
            if (!StringUtils.hasText(request.token())) {
                throw new CatxiException(FcmErrorCode.INVALID_FCM_TOKEN_FORMAT);
            }

            // 기존 토큰이 있고 같은 토큰인 경우
            if (request.token().equals(member.getFcmToken())) {
                log.info("동일한 FCM 토큰 - Member ID: {}", member.getId());
                return new FcmTokenUpdateRes(
                        member.getId(),
                        member.getFcmTokenUpdatedAt(),
                        true
                );
            }

            // 새 토큰 업데이트
            member.updateFcmToken(request.token());
            memberRepository.save(member);
            log.info("FCM 토큰 업데이트 - Member ID: {}", member.getId());

            return new FcmTokenUpdateRes(
                    member.getId(),
                    member.getFcmTokenUpdatedAt(),
                    true
            );

        } catch (CatxiException e) {
            throw e;
        } catch (Exception e) {
            log.error("FCM 토큰 처리 실패 - Member ID: {}, Error: {}", member.getId(), e.getMessage(), e);
            throw new CatxiException(FcmErrorCode.FCM_TOKEN_UPDATE_FAILED);
        }
    }

    @Transactional
    public void deleteFcmToken(Member member) {
        try {
            if (member.getFcmToken() == null) {
                log.info("삭제할 FCM 토큰이 없음 - Member ID: {}", member.getId());
                return; // 이미 토큰이 없는 경우, 바로 종료
            }

            member.updateFcmToken(null);
            memberRepository.save(member);
            log.info("FCM 토큰 삭제 완료 - Member ID: {}", member.getId());

        } catch (Exception e) {
            log.error("FCM 토큰 삭제 실패 - Member ID: {}, Error: {}", member.getId(), e.getMessage(), e);
            throw new CatxiException(FcmErrorCode.FCM_TOKEN_DELETION_FAILED);
        }
    }

    public List<String> getActiveTokens(Member member) {
        try {
            return member.getFcmToken() != null 
                ? List.of(member.getFcmToken()) 
                : List.of();
        } catch (Exception e) {
            log.warn("활성 FCM 토큰 조회 실패 - Member ID: {}, 알림 발송 건너뜀", member.getId());
            return List.of(); // 빈 리스트 반환으로 알림 시스템 중단 방지
        }
    }

    @Transactional
    public void removeInvalidFcmToken(String invalidToken) {
        try {
            Member member = memberRepository.findByFcmToken(invalidToken);
            if (member != null) {
                member.updateFcmToken(null);
                memberRepository.save(member);
                log.info("유효하지 않은 FCM 토큰 제거 완료 - Member ID: {}", member.getId());
            }
        } catch (Exception e) {
            log.error("FCM 토큰 제거 실패 - Token: {}, Error: {}", 
                    invalidToken != null ? invalidToken.substring(0, Math.min(20, invalidToken.length())) + "..." : "null", 
                    e.getMessage(), e);
        }
    }
}