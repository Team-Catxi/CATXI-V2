package com.project.catxi.member.service;

import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.dto.MatchHistoryRes;
import com.project.catxi.member.converter.MemberConverter;
import com.project.catxi.member.domain.MatchHistory;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MatchHistoryRepository;
import com.project.catxi.member.repository.MemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class MatchHistoryService {

  private final MatchHistoryRepository matchHistoryRepository;
  private final MemberRepository memberRepository;

  private final ChatParticipantRepository chatParticipantRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final ChatRoomRepository chatRoomRepository;

  //단 건 조회용
  public MatchHistoryRes getHistoryById(Long historyId, String email) {
    Member user = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

     MatchHistory history = matchHistoryRepository.findById(historyId)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MATCH_NOT_FOUND));

    if (!history.getUser().equals(user)) {
      throw new AccessDeniedException("본인의 이력만 조회할 수 있습니다.");
    }
    return MemberConverter.toSingleResDTO(history,user.getMembername());
  }
  //최근 내역 2건 조회용
  //  public List<MatchHistoryRes> getRecentHistoryTop2(String email) {
  //    Member user = memberRepository.findByEmail(email)
  //        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
  //
  //    return matchHistoryRepository.findTop2ByUserOrderByCreatedAtDesc(user).stream()
  //        .map(MemberConverter::toSingleResDTO)
  //        .toList();
  //  }

  //전체 내역 조회용
  public Slice<MatchHistoryRes> getScrollHistory(String email, Pageable pageable) {
    Member user = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    String membername = user.getMembername();
    Slice<MatchHistory> histories = matchHistoryRepository.findHistoriesByUserOrFella(email, membername, pageable);

    return histories.map(history->MemberConverter.toAllResDTO(history,membername));
  }

  //매치 히스토리 저장 : 채팅방, 채팅내역, 채팅 참가자 제거
  public void saveMatchHistory(Long roomId, String hostEmail) {
    ChatRoom room = chatRoomRepository.findById(roomId)
        .orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

    Member member = memberRepository.findByEmail(hostEmail)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    if (!room.getHost().equals(member)) {
      throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
    }

    List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);

    // 채팅방 상태가 MATCHED가 아니거나 참여자가 1명 이하인 경우 매칭 기록을 저장하지 않음
    if(!room.getStatus().equals(RoomStatus.MATCHED) || participants.size()==1){
      return;
    }

    List<String> fellas = participants.stream()
        .map(participant -> participant.getMember().getNickname())
        .toList();

    for (ChatParticipant participant : participants) {
      Member part = participant.getMember();
      part.setMatchCount(part.getMatchCount() + 1);
      saveMatchHistory(room, part, fellas);
      memberRepository.save(part);
    }

  }

  private void saveMatchHistory(ChatRoom chatRoom, Member member,List<String> fellasList) {
    MatchHistory matchHistory = MatchHistory.builder()
        .user(member)
        .startPoint(chatRoom.getStartPoint())
        .endPoint(chatRoom.getEndPoint())
        .fellas(fellasList)
        .createdAt(LocalDateTime.now())
        .matchedAt(chatRoom.getMatchedAt())
        .build();
    matchHistoryRepository.save(matchHistory);
  }

}
