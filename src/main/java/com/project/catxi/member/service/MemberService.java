package com.project.catxi.member.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.member.dto.MemberProfileRes;
import com.project.catxi.member.dto.SignUpDTO;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.converter.MemberConverter;
import com.project.catxi.member.repository.MemberRepository;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemberService {

  private final MemberRepository memberRepository;
  private final BCryptPasswordEncoder bCryptPasswordEncoder;

  public MemberService(MemberRepository memberRepository, BCryptPasswordEncoder bCryptPasswordEncoder) {
    this.memberRepository = memberRepository;
    this.bCryptPasswordEncoder = bCryptPasswordEncoder;
  }

  public Long signUp(SignUpDTO dto) {

    // 유효성은 컨트롤러에서 @Valid 처리
    Member member = Member.builder()
        .membername(dto.getMembername())
        .nickname(dto.getNickname())
        .studentNo(dto.getStudentNo())
        .password(bCryptPasswordEncoder.encode(dto.getPassword()))
        .matchCount(0)
        .createdAt(LocalDateTime.now())
        .build();

    try {
      Member saved = memberRepository.save(member);
      return saved.getId();
    } catch (DataIntegrityViolationException ex) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
    }
  }

  @Transactional
  public void delete(String email){
    Member member = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    member.delete();
  }

  public MemberProfileRes getProfile(String email) {
    Member member = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    return MemberConverter.toMemberProfileDTO(member);
  }

}
