package com.project.catxi.chat.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.dto.RoomDeletedEvent;
import com.project.catxi.chat.dto.RoomEventMessage;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RoomDeletedEventListener {

	private final @Qualifier("chatPubSub") StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void on(RoomDeletedEvent e) {
		try {
			String json = objectMapper.writeValueAsString(
				new RoomEventMessage(e.roomId(), "DELETED", e.hostNickname() + " 님이 방을 삭제했습니다.")
			);
			// 방 단위 브로드캐스트
			redis.convertAndSend("roomdeleted:" + e.roomId(), json);

		} catch (Exception ex) {
			throw new RuntimeException("RoomDeletedEvent publish failed", ex);
		}
	}
}
