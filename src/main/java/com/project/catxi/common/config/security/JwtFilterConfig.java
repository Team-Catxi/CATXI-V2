package com.project.catxi.common.config.security;

import com.project.catxi.common.auth.infra.TokenBlacklistRepository;
import com.project.catxi.common.auth.service.TokenService;
import com.project.catxi.common.jwt.JwtFilter;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.member.repository.MemberRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
public class JwtFilterConfig {

  private final JwtUtil jwtUtil;
  private final TokenService tokenService;
  private final MemberRepository memberRepository;
  private final TokenBlacklistRepository tokenBlacklistRepository;

  public JwtFilterConfig(JwtUtil jwtUtil, TokenService tokenService, MemberRepository memberRepository, TokenBlacklistRepository tokenBlacklistRepository) {
    this.jwtUtil = jwtUtil;
    this.tokenService = tokenService;
    this.memberRepository = memberRepository;
    this.tokenBlacklistRepository = tokenBlacklistRepository;
  }

  public void configureJwtFilters(HttpSecurity http) throws Exception {
    // JwtFilter - 토큰 검증 및 인증 객체 설정
    http.addFilterBefore(
        new JwtFilter(jwtUtil, tokenService, memberRepository, tokenBlacklistRepository), UsernamePasswordAuthenticationFilter.class);
  }

}