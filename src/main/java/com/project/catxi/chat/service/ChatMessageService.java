package com.project.catxi.chat.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.ChatMessagePageRes;
import com.project.catxi.chat.dto.ChatMessageRes;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.FcmErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.MessageType;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.fcm.domain.FcmOutbox;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.fcm.repository.FcmOutboxRepository;

import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {

	private final ChatRoomRepository chatRoomRepository;
	private final MemberRepository memberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ObjectMapper objectMapper;
	private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
	private final FcmOutboxRepository fcmOutboxRepository;

	public void saveMessage(Long roomId, ChatMessageSendReq req) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member sender = memberRepository.findByEmail(req.email())
				.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		// 강퇴된 사용자 검증 - ChatParticipant 테이블에서 직접 확인
		if (!chatParticipantRepository.existsByChatRoomAndMember(room, sender)) {
			log.warn("[메시지 전송 차단] 강퇴된 사용자: email={}, roomId={}", req.email(), roomId);
			throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
		}

		ChatMessage chatMsg = ChatMessage.builder()
				.chatRoom(room)
				.member(sender)
				.content(req.message())
				.msgType(MessageType.CHAT)
				.build();

		ChatMessage savedMessage = chatMessageRepository.save(chatMsg);

		processChatFcmNotificationWithMessage(room, sender, savedMessage, req.message());
	}

	/**
	 * FCM 알림 처리 - Outbox 패턴 적용
	 * MySQL 트랜잭션 내에서 fcm_outbox에 저장
	 */
	private void processChatFcmNotificationWithMessage(ChatRoom room, Member sender, ChatMessage savedMessage,
			String message) {
		try {
			log.info("FCM Outbox 저장 시작: RoomId={}, MessageId={}",
					room.getRoomId(), savedMessage.getId());

			String senderName = sender.getNickname() != null ? sender.getNickname() : sender.getMembername();

			// 방에 참여한 다른 사용자들 조회 (발송자 제외)
			List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);

			// 모든 참여자의 Outbox 이벤트를 한 번에 생성
			List<FcmOutbox> outboxList = participants.stream()
					.filter(participant -> participant.getMember() != null)
					.filter(participant -> !participant.getMember().getId().equals(sender.getId()))
					.map(participant -> {
						try {
							FcmNotificationEvent event = FcmNotificationEvent.createChatMessage(
									participant.getMember().getId(),
									room.getRoomId(),
									savedMessage.getId(),
									senderName,
									message);
							String payload = objectMapper.writeValueAsString(event);
							return FcmOutbox.builder()
									.eventId(event.eventId())
									.eventType(FcmOutbox.EventType.CHAT_MESSAGE)
									.payload(payload)
									.build();
						} catch (JsonProcessingException e) {
							log.error("FCM 이벤트 직렬화 실패: Target={}", participant.getMember().getId(), e);
							throw new CatxiException(FcmErrorCode.FCM_OUTBOX_SAVE_FAILED);
						}
					})
					.toList();

			// 단건 save() N회 → saveAll() 1회로 DB 왕복 최소화
			fcmOutboxRepository.saveAll(outboxList);

			log.info("FCM Outbox 저장 완료: RoomId={}, MessageId={}, count={}",
					room.getRoomId(), savedMessage.getId(), outboxList.size());

		} catch (Exception e) {
			log.error("FCM Outbox 저장 실패: RoomId={}, Error={}",
					room.getRoomId(), e.getMessage(), e);
			throw e;
		}
	}

	public ChatMessagePageRes getChatHistory(Long roomId, String email, Long cursor, int size) {

		Member member = memberRepository.findByEmail(email)
				.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		if (!chatParticipantRepository.existsByChatRoomAndMember(room, member)) {
			throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
		}

		// size+1 개 조회해서 다음 페이지 존재 여부 판단
		PageRequest pageRequest = PageRequest.of(0, size + 1);
		List<ChatMessage> fetched = (cursor == null)
				? chatMessageRepository.findByChatRoomOrderByIdDesc(room, pageRequest)
				: chatMessageRepository.findByChatRoomAndIdLessThanOrderByIdDesc(room, cursor, pageRequest);

		boolean hasNext = fetched.size() > size;
		List<ChatMessage> page = hasNext ? fetched.subList(0, size) : fetched;

		// DESC로 조회한 결과를 ASC(오래된 순)로 뒤집어 반환
		Collections.reverse(page);

		Long nextCursor = hasNext ? page.get(0).getId() : null;

		List<ChatMessageRes> messages = page.stream()
				.map(m -> new ChatMessageRes(
						m.getMember() != null ? m.getMember().getEmail() : "[SYSTEM]",
						m.getId(),
						room.getRoomId(),
						m.getMember() != null ? m.getMember().getId() : null,
						m.getMember() != null ? m.getMember().getNickname() : "[SYSTEM]",
						m.getContent(),
						m.getCreatedTime()))
				.toList();

		return new ChatMessagePageRes(messages, nextCursor, hasNext);
	}

	public void sendSystemMessage(Long roomId, String content) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatMessage systemMsg = ChatMessage.builder()
				.chatRoom(chatRoom)
				.member(null)
				.content(content)
				.msgType(MessageType.SYSTEM)
				.build();

		chatMessageRepository.save(systemMsg);

		ChatMessageSendReq dto = new ChatMessageSendReq(
				roomId,
				"[SYSTEM]",
				content,
				LocalDateTime.now());

		try {
			String json = objectMapper.writeValueAsString(dto);
			redisTemplate.convertAndSend("chat", json); // ✅
		} catch (JsonProcessingException e) {
			throw new RuntimeException("시스템 메시지 직렬화 실패", e);
		}

	}
}
