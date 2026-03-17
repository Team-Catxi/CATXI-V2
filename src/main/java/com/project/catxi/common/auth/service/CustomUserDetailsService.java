package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final MemberRepository memberRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

    //Email 기준 조회
    Member member = memberRepository.findByEmail(username)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    log.info("✅로그인 시도 : Email: {}, Status: {}", member.getEmail(), member.getStatus());

    // 회원 상태 조회
    switch (member.getStatus()) {
      case INACTIVE:
        throw new DisabledException("탈퇴한 회원입니다.");
      case PENDING:
        // PENDING 상태도 인증은 허용
        return new CustomUserDetails(member);
      case ACTIVE:
        return new CustomUserDetails(member);
      default:
        throw new DisabledException("유효하지 않은 회원 상태입니다.");
    }
  }
}
