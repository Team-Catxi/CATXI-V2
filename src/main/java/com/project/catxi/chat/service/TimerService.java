package com.project.catxi.chat.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.RoomEventMessage;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.RoomStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TimerService {

	private final ObjectMapper objectMapper;
	private final RedisTemplate<String, String> redisTemplate;
	private final TaskScheduler taskScheduler;
	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ChatMessageService chatMessageService;

	public TimerService(
		RedisTemplate<String, String> redisTemplate,
		@Qualifier("commonTaskScheduler") TaskScheduler taskScheduler,
		ChatRoomRepository chatRoomRepository,
		ChatParticipantRepository chatParticipantRepository,
		ChatMessageService chatMessageService,
		ObjectMapper objectMapper
	) {
		this.redisTemplate = redisTemplate;
		this.taskScheduler = taskScheduler;
		this.chatRoomRepository = chatRoomRepository;
		this.chatParticipantRepository = chatParticipantRepository;
		this.chatMessageService = chatMessageService;
		this.objectMapper = objectMapper;
	}

	public void scheduleReadyTimeout(String roomId) {
		ChatRoom room = chatRoomRepository.findById(Long.valueOf(roomId))
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		long participantCount = chatParticipantRepository.countByChatRoom(room);

		// Redis에 당시 참여자 수 저장
		redisTemplate.opsForValue().set("ready:" + roomId, String.valueOf(participantCount), Duration.ofSeconds(25));

		// TaskScheduler로 10초 뒤 작업 예약
		taskScheduler.schedule(() -> {
				try {
					checkAndUpdateRoomStatus(roomId);
				} catch (Exception e) {
					log.error("Error checking and updating room status for roomId: {}", roomId, e);
				}
			},
			Instant.now().plusSeconds(20)
		);
	}

	@Transactional
	public void checkAndUpdateRoomStatus(String roomId) {
		ChatRoom room = chatRoomRepository.findById(Long.valueOf(roomId))
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		String savedCountStr = redisTemplate.opsForValue().get("ready:" + roomId);

		if (room.getStatus() != RoomStatus.READY_LOCKED) {
			return;
		}

		if (savedCountStr == null) {
			// Redis 키가 사라졌거나 문제가 생긴 경우
			chatParticipantRepository.updateIsReadyFalseExceptHost(room.getRoomId());
			room.setStatus(RoomStatus.WAITING);
		} else {
			long savedCount = Long.parseLong(savedCountStr);
			long currentCount = chatParticipantRepository.countByChatRoomAndIsReady(room, true);
			Long roomIdLong = Long.valueOf(roomId);

			if (savedCount == currentCount) {
				room.matchedStatus(RoomStatus.MATCHED);
				publishRoomResult(roomIdLong, "MATCHED", "모든 참가자가 준비되었습니다. 매칭이 완료되었습니다");
			} else {
				// 준비하지 않은 참가자 퇴장 메시지 전송
				List<String> removeNickNames = chatParticipantRepository.findNickNameByChatRoomAndIsReadyFalse(room);

				// DB에서 준비하지 않은 참가자 삭제
				chatParticipantRepository.deleteAllByChatRoomAndIsReadyFalse(room);
				chatParticipantRepository.updateIsReadyFalseExceptHost(room.getRoomId());

				for (String nickName : removeNickNames) {
					String systemMessage = nickName + " 님이 퇴장하셨습니다.";
					chatMessageService.sendSystemMessage(roomIdLong, systemMessage);
				}

				room.setStatus(RoomStatus.WAITING);
				publishRoomResult(roomIdLong, "RETURN_WAITING", "일부 참가자가 준비하지 않아 대기 상태로 돌아갑니다.");
			}
		}

		chatRoomRepository.save(room);
		redisTemplate.delete("ready:" + roomId);

		log.info("[TimerService] 방 상태 업데이트 완료: {} at {}", roomId, LocalDateTime.now());
	}


	private void publishRoomResult(Long roomId, String type, String content) {
		try {
			RoomEventMessage evt = new RoomEventMessage(roomId, type, content);
			String json = objectMapper.writeValueAsString(evt);
			redisTemplate.convertAndSend("readyresult:" + roomId, json);
		} catch (Exception e) {
			log.error("Failed to publish room status event for roomId: {}", roomId, e);
		}
	}
}