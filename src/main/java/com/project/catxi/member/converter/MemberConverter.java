package com.project.catxi.member.converter;

import com.project.catxi.member.dto.MatchHistoryRes;
import com.project.catxi.member.dto.MemberProfileRes;
import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MemberConverter {

  public static MemberProfileRes toMemberProfileDTO(Member member) {
    return new MemberProfileRes(
        member.getMembername(),
        member.getStudentNo(),
        member.getMatchCount());
  }


  public static MatchHistoryRes toSingleResDTO(MatchHistory history, String membername) {
    return new MatchHistoryRes(
        history.getId(),
        history.getMatchedAt(),
        history.getStartPoint().name(),
        history.getEndPoint().name(),
        getFellaUsers(history.getFellas(), membername)
    );
  }

  public static MatchHistoryRes toAllResDTO(MatchHistory history, String membername) {

    return new MatchHistoryRes(
        history.getId(),
        history.getMatchedAt(),
        history.getStartPoint().name(),
        history.getEndPoint().name(),
        getFellaUsers(history.getFellas(), membername)
    );
  }

  // 마스킹 처리 메서드
  private static String maskName(String name) {
    if (name == null || name.isBlank()) return "";
    if (name.length() == 1) return name;
    return name.charAt(0) + "*".repeat(name.length() - 1);
  }

  // fellas 반환 (필터링 미포함)
  private static List<String> maskFellas(List<String> fellas) {
    return fellas.stream()
        .map(MemberConverter::maskName)
        .toList();
  }

  // fellas - 조회 기준 user (마스킹 처리 포함)
  private static List<String> getFellaUsers(List<String> fellas, String membername) {
    log.info("로그인 유저 : {}", membername);
    log.info("Fellas : {}", fellas);

    List<String> result = fellas.stream()
        .filter(name -> !name.equals(membername))
        .map(MemberConverter::maskName)
        .toList();

    log.info("출력 Fellas: {}", result);
    return result;
  }

}
