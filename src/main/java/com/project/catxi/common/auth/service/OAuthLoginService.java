package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.auth.infra.CodeCache;
import com.project.catxi.common.auth.infra.CookieUtil;
import com.project.catxi.common.auth.infra.RefreshTokenRepository;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.KakaoUtil;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.common.jwt.JwtTokenProvider;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthLoginService {

    private final KakaoUtil kakaoUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CodeCache codeCache;

    public Member kakaoLoginProcess(String accessCode, HttpServletResponse response) {
        
        // 중복 코드 차단
        if (codeCache.isDuplicate(accessCode)) {
            log.warn("🚨중복 code 요청 차단 code = {}", accessCode);
            throw new CatxiException(MemberErrorCode.DUPLICATE_AUTHORIZE_CODE);
        }
        try {
            return oAuthLogin(accessCode, response);
        } catch (Exception e) {
            log.error("[카카오 로그인 실패] code = {}, error = {}", accessCode, e.getMessage());
            codeCache.remove(accessCode);
            throw new CatxiException(MemberErrorCode.ACCESS_EXPIRED);
        }
    }

    public Member oAuthLogin(String accessCode, HttpServletResponse response) {
        
        //카카오 토큰 요청
        KakaoDTO.kakaoToken kakaoToken = kakaoUtil.requestToken(accessCode);
        //사용자 정보 요청
        KakaoDTO.KakaoProfile kakaoProfile = kakaoUtil.requestProfile(kakaoToken);
        
        String email = kakaoProfile.kakao_account().email();
        Member user = memberRepository.findByEmail(email)
            .orElseGet(() -> createNewUser(kakaoProfile));

        validateNewUser(user);
        
        //JWT 발급 후 응답 헤더에 추가
        String jwt = loginProcess(response, user);
        
        //분기처리
        setNewUser(response, user);
        
        log.info("[카카오 프로필] email = {}", email);
        log.info("✅JWT 발급 : {}", jwt);

        return user;
    }

    private Member createNewUser(KakaoDTO.KakaoProfile kakaoProfile) {
        String email = kakaoProfile.kakao_account().email();
        String name = kakaoProfile.kakao_account().profile().nickname();

        log.info(">> name: {}", name);
        log.info(">> email: {}", email);

        Member newUser = Member.builder()
            .email(email)
            .membername(name)
            .password("NO_PASSWORD")
            .matchCount(0)
            .role("ROLE_USER")
            .status(MemberStatus.PENDING)
            .build();

        return memberRepository.save(newUser);
    }

    private String loginProcess(HttpServletResponse response, Member user) {
        String email = user.getEmail();
        String role = user.getRole();

        String accessToken = jwtTokenProvider.generateAccessToken(email, role);
        response.setHeader("access", accessToken);
        
        String refreshToken = jwtTokenProvider.generateRefreshToken(email, role);
        refreshTokenRepository.save(email, refreshToken, Duration.ofDays(30));
        
        ResponseCookie refreshCookie = CookieUtil.createCookie(refreshToken, Duration.ofDays(30));
        response.setHeader("Set-Cookie", refreshCookie.toString());
        
        log.info("✅ [헤더에 담은 JWT] access = {}", accessToken);
        log.info("✅ [쿠키에 담은 RefreshToken] refresh = {}", refreshToken);

        return accessToken;
    }

    //회원 Status 점검
    private void validateNewUser(Member user) {
        log.info("🚨회원 Status = {}", user.getStatus());
        if (user.getStatus() == MemberStatus.INACTIVE) {
            throw new MemberHandler(MemberErrorCode.ACCESS_FORBIDDEN);
        }
    }

    //헤더에 setNewUser
    private void setNewUser(HttpServletResponse response, Member user) {
        boolean isNewUser = user.getStatus() == MemberStatus.PENDING;
        response.setHeader("isNewUser", String.valueOf(isNewUser));
    }
}