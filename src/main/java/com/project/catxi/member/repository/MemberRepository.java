package com.project.catxi.member.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.member.domain.Member;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository   extends JpaRepository<Member, Long> {

  Optional<Member> findByMembername(String membername);

  Optional<Member> findByNickname(String nickname);

  // 이메일 기준
  Optional<Member> findByEmail(String email);
  // 이메일 중복검사
  boolean existsByEmail(String email);

  //학번 검증용
  boolean existsByStudentNo(String studentNo);

  //닉네임 중복 조회용
  boolean existsByNickname(String nickname);

  // FCM 토큰으로 회원 조회
  Member findByFcmToken(String fcmToken);

  //삭제된 회원 조회 불가
  //Optional<Member> findByEmailAndDeletedFalse(String email, MemberStatus status);
}