package com.project.catxi.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
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

			// 방에 참여한 다른 사용자들 조회 (발송자 제외)
			List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);

			// 각 참여자별로 Outbox에 저장
			participants.stream()
					.filter(participant -> participant.getMember() != null)
					.filter(participant -> !participant.getMember().getId().equals(sender.getId()))
					.forEach(participant -> {
						saveChatNotificationToOutbox(
								participant.getMember().getId(),
								room.getRoomId(),
								savedMessage.getId(),
								sender.getNickname() != null ? sender.getNickname() : sender.getMembername(),
								message);
					});

			log.info("FCM Outbox 저장 완료: RoomId={}, MessageId={}",
					room.getRoomId(), savedMessage.getId());

		} catch (Exception e) {
			log.error("FCM Outbox 저장 실패: RoomId={}, Error={}",
					room.getRoomId(), e.getMessage(), e);
			// 예외를 다시 던져서 전체 트랜잭션 롤백
			throw e;
		}
	}

	/**
	 * 채팅 알림을 Outbox에 저장
	 */
	private void saveChatNotificationToOutbox(Long targetMemberId, Long roomId, Long messageId,
			String senderNickname, String message) {
		try {
			// FcmNotificationEvent 생성
			FcmNotificationEvent event = FcmNotificationEvent.createChatMessage(
					targetMemberId, roomId, messageId, senderNickname, message);

			// JSON으로 직렬화
			String payload = objectMapper.writeValueAsString(event);

			// Outbox에 저장
			FcmOutbox outbox = FcmOutbox.builder()
					.eventId(event.eventId())
					.eventType(FcmOutbox.EventType.CHAT_MESSAGE)
					.payload(payload)
					.build();

			fcmOutboxRepository.save(outbox);

			log.debug("FCM Outbox 저장: EventId={}, Target={}", event.eventId(), targetMemberId);

		} catch (JsonProcessingException e) {
			log.error("FCM 이벤트 직렬화 실패: Target={}", targetMemberId, e);
			throw new CatxiException(FcmErrorCode.FCM_OUTBOX_SAVE_FAILED);
		}
	}

	public List<ChatMessageRes> getChatHistory(Long roomId, String email) {

		Member member = memberRepository.findByEmail(email)
				.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		if (!chatParticipantRepository.existsByChatRoomAndMember(room, member)) {
			throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
		}

		return chatMessageRepository.findByChatRoomOrderByCreatedTimeAsc(room)
				.stream()
				.map(m -> new ChatMessageRes(
						m.getMember() != null ? m.getMember().getEmail() : "[SYSTEM]",
						m.getId(),
						room.getRoomId(),
						m.getMember() != null ? m.getMember().getId() : null,
						m.getMember() != null ? m.getMember().getNickname() : "[SYSTEM]",
						m.getContent(),
						m.getCreatedTime()))
				.toList();

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
