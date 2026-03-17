package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.auth.infra.CookieUtil;
import com.project.catxi.common.auth.infra.RefreshTokenRepository;
import com.project.catxi.common.auth.infra.TokenBlacklistRepository;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.TokenDTO;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.common.jwt.JwtTokenProvider;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final JwtUtil jwtUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFRESH_COOKIE = "refresh";
    private static final String HEADER_REF = "X-Access-Token-Refreshed";
    private static final String HEADER_EXP = "Access-Control-Expose-Headers";

    //reissue
    public TokenDTO.Response reissueAccessToken(String refreshToken, HttpServletResponse response) {
        validateRefreshToken(refreshToken);
        
        //파싱 후 이메일 추출
        Claims claims = jwtUtil.parseJwt(refreshToken);
        String email = jwtUtil.getEmail(claims);

        //레디스에 저장된 토큰 값 비교
        if (!refreshTokenRepository.isValid(email, refreshToken)) {
            refreshTokenRepository.delete(email);
            throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        //사용자 정보 조회하여 role 가져오기
        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        
        //토큰 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(email, member.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, member.getRole());

        //Token Rotate
        refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, Duration.ofDays(30));

        //RefreshToken 전송
        ResponseCookie refreshCookie = CookieUtil.createCookie(newRefreshToken, Duration.ofDays(30));
        response.setHeader("Set-Cookie", refreshCookie.toString());
        
        log.info("✅Rotate 이전 RT 값 : {}", refreshToken);
        log.info("🚨Rotate 이후 RT 값 : {}", newRefreshToken);

        return new TokenDTO.Response(newAccessToken, newRefreshToken);
    }

    //로그아웃 (AccessToken 블랙리스트 추가)
    public void logout(HttpServletRequest request, String refreshToken, HttpServletResponse response) {
        try {
            // AccessToken 블랙리스트에 추가
            String authorization = request.getHeader("Authorization");

            if (authorization != null && authorization.startsWith("Bearer ")) {
                String accessToken = authorization.substring("Bearer ".length());
                //토큰 유효성 검사
                if (jwtUtil.validateToken(accessToken)) {
                    // 토큰 유효기간 계산
                    Claims claims = jwtUtil.parseJwt(accessToken);
                    Date expiration = claims.getExpiration();
                    long remainTime = expiration.getTime() - System.currentTimeMillis();
                    
                    if (remainTime > 0) {
                        // 블랙리스트에 추가
                        tokenBlacklistRepository.addTokenToBlacklist(accessToken, Duration.ofMillis(remainTime));
                        
                        // 해당 사용자의 모든 refreshToken도 삭제 (보안 강화)
                        String email = jwtUtil.getEmail(claims);
                        refreshTokenRepository.delete(email);
                        
                        log.info("✅ AccessToken 블랙리스트 등록 및 RefreshToken 삭제: {}", email);
                    }
                }
            }

            if (refreshToken != null && !refreshToken.isBlank() && jwtUtil.validateToken(refreshToken)) {
                //RefreshToken Redis에서 삭제
                refreshTokenRepository.deleteByToken(refreshToken);
                log.info("✅ RefreshToken 삭제 완료");
            }
        } catch (Exception e) {
            log.warn("🚨로그아웃 중 오류 발생: {}", e.getMessage());
        } finally {
            response.addHeader("Set-Cookie", CookieUtil.deleteCookie().toString());
        }
    }
    

    @Transactional
    public void catxiSignup(String email, KakaoDTO.CatxiSignUp dto) {
        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

        validateCatxiSignUp(dto);
        
        member.setNickname(dto.nickname());
        member.setStudentNo(dto.StudentNo());
        member.setStatus(MemberStatus.ACTIVE);
    }

    public boolean isNNDuplicate(String nickname) {
        return memberRepository.existsByNickname(nickname);
    }

    //리프레시토큰 검증
    private void validateRefreshToken(String refreshToken) {

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        if (!jwtUtil.validateToken(refreshToken)) {
            log.warn("🚨 RefreshToken 유효성 검사 실패");
            throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        log.info("✅ RefreshToken 유효성 검사 통과");
    }

    //회원가입 검증
    private void validateCatxiSignUp(KakaoDTO.CatxiSignUp dto) {
        //닉네임 제한
        if (dto.nickname() == null || dto.nickname().length() > 9) {
            throw new CatxiException(MemberErrorCode.INVALID_NICKNAME_LENGTH);
        }
        //닉네임 중복체크
        if (memberRepository.existsByNickname(dto.nickname())) {
            throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
        }
        //학번 정확히 9글자 숫자
        if (dto.StudentNo() == null || !dto.StudentNo().matches("\\d{9}")) {
            throw new CatxiException(MemberErrorCode.INVALID_STUDENT_NO);
        }
        //학번 중복체크
        if (memberRepository.existsByStudentNo(dto.StudentNo())) {
            throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
        }
    }

    //무중단 액세스 토큰 재발급 로직
    public String zeroDownRefresh(String email, HttpServletRequest request, HttpServletResponse response) {
        try {

            //Refresh Token 추출 및 검증
            String refreshToken = extractCookie(request, REFRESH_COOKIE);
            if (refreshToken == null || !jwtUtil.validateToken(refreshToken) || !refreshTokenRepository.isValid(email, refreshToken)) {
                return null;
            }

            Member member = memberRepository.findByEmail(email).orElse(null);
            if (member == null) {
                return null;
            }

            //토큰 생성
            String newAccessToken = jwtTokenProvider.generateAccessToken(email, member.getRole());
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, member.getRole());

            //토큰 rotate
            refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, Duration.ofDays(30));
            response.setHeader("Set-Cookie", CookieUtil.createCookie(newRefreshToken, Duration.ofDays(30)).toString());
            response.setHeader(AUTH_HEADER, BEARER_PREFIX + newAccessToken);
            response.setHeader(HEADER_REF, "true");
            exposeHeaders(response, AUTH_HEADER, HEADER_REF);

            log.info("✅ 토큰 재발급: {}", email);
            return newAccessToken;
            
        } catch (Exception e) {
            log.warn("토큰 재발급 실패: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void exposeHeaders(HttpServletResponse response, String... headers) {
        String existing = response.getHeader(HEADER_EXP);
        String toAdd = String.join(",", headers);
        response.setHeader(HEADER_EXP, existing == null ? toAdd : existing + "," + toAdd);
    }
}