package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.auth.infra.RefreshTokenRepository;
import com.project.catxi.common.auth.infra.TokenBlacklistRepository;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;

    // 사용자를 블랙리스트에 추가 (영구)
    @Transactional
    public void addUserToBlacklistPermanent(Long userId) {
        Member member = memberRepository.findById(userId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        
        // 사용자를 블랙리스트에 추가
        tokenBlacklistRepository.addUserToBlacklist(userId.toString());
        
        // 해당 사용자의 refreshToken을 Redis에서 삭제
        refreshTokenRepository.delete(member.getEmail());
        
        log.info("✅ 유저 블랙리스트 추가 및 RefreshToken 삭제: {} ({})", member.getEmail(), userId);
    }

    // 사용자를 블랙리스트에서 제거
    @Transactional
    public void removeUserFromBlacklist(Long userId) {
        Member member = memberRepository.findById(userId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        
        tokenBlacklistRepository.removeUserFromBlacklist(userId.toString());
        log.info("✅ 유저 블랙리스트 해제: {} ({})", member.getEmail(), userId);
    }

    // 사용자가 블랙리스트에 있는지 확인
    public boolean isUserBlacklisted(Long userId) {
        return tokenBlacklistRepository.isUserBlacklisted(userId.toString());
    }
}