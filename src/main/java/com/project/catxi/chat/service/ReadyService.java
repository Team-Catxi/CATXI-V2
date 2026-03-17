package com.project.catxi.chat.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.ReadyMessageEvent;
import com.project.catxi.chat.dto.ReadyMessageRes;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.FcmErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.fcm.domain.FcmOutbox;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.fcm.repository.FcmOutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadyService {
	private final ApplicationEventPublisher eventPublisher;
	private final ChatParticipantRepository chatParticipantRepository;
	private final MemberRepository memberRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final TimerService timerService;
	private final FcmOutboxRepository fcmOutboxRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public void requestReady(Long roomId, String email) {
		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member member = memberRepository.findByEmail(email)
				.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
				.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		if (!participant.isHost()) {
			throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
		}

		if (!room.getStatus().equals(RoomStatus.WAITING)) {
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_WAITING);
		}

		room.setStatus(RoomStatus.READY_LOCKED);

		/*
		 * 레디 요청 10초 이후 (레디 요청 당시의 참여자 수 == 10초 이후 참여자 수)가
		 * - True라면 MATCHED로 변경
		 * - False라면 WAITING으로 변경
		 */
		ReadyMessageRes payload = ReadyMessageRes.readyRequest(roomId, member);
		eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));

		// FCM 준비 요청 알림 발송 (방장 제외한 모든 참여자에게)
		sendReadyRequestNotification(room, member);

		timerService.scheduleReadyTimeout(roomId.toString());

	}

	@Transactional
	public void acceptReady(Long roomId, String email) {
		Member member = memberRepository.findByEmail(email)
				.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
				.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		checkParticipant(room, participant);

		participant.setReady(true);

		ReadyMessageRes payload = ReadyMessageRes.readyAccept(roomId, member);
		eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));

	}

	@Transactional
	public void rejectReady(Long roomId, String email) {
		Member member = memberRepository.findByEmail(email)
				.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(roomId)
				.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
				.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		checkParticipant(room, participant);

		ReadyMessageRes payload = ReadyMessageRes.readyDeny(roomId, member);
		eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));

	}

	private void sendReadyRequestNotification(ChatRoom room, Member host) {
		try {
			log.info("Ready FCM Outbox 저장 시작: RoomId={}", room.getRoomId());

			// 방에 참여한 다른 사용자들 조회 (방장 제외)
			List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);

			List<Long> targetMemberIds = participants.stream()
					.filter(participant -> participant.getMember() != null)
					.filter(participant -> !participant.getMember().getId().equals(host.getId()))
					.map(participant -> participant.getMember().getId())
					.toList();

			if (!targetMemberIds.isEmpty()) {
				// FcmNotificationEvent 생성
				FcmNotificationEvent event = FcmNotificationEvent.createReadyRequest(
						targetMemberIds, room.getRoomId());

				// JSON으로 직렬화
				String payload = objectMapper.writeValueAsString(event);

				// Outbox에 저장
				FcmOutbox outbox = FcmOutbox.builder()
						.eventId(event.eventId())
						.eventType(FcmOutbox.EventType.READY_REQUEST)
						.payload(payload)
						.build();

				fcmOutboxRepository.save(outbox);

				log.info("FCM 준비요청 Outbox 저장 완료 - Room ID: {}, Targets: {}",
						room.getRoomId(), targetMemberIds.size());
			}

		} catch (Exception e) {
			log.error("준비요청 FCM Outbox 저장 실패 - Room ID: {}, Host: {}",
					room.getRoomId(), host.getId(), e);
			throw new CatxiException(FcmErrorCode.FCM_OUTBOX_SAVE_FAILED);
		}
	}

	private void checkParticipant(ChatRoom room, ChatParticipant participant) {
		if (!room.getStatus().equals(RoomStatus.READY_LOCKED)) {
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_READY_LOCKED);
		}
		if (participant.isHost()) {
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		}

		if (participant.isReady()) {
			throw new CatxiException(ChatParticipantErrorCode.ALREADY_READY);
		}
	}

}