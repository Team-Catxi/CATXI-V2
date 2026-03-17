package com.project.catxi.chat.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.dto.ReadyMessageEvent;
import com.project.catxi.chat.service.RedisPubSubService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReadyMessageEventListener {

	private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReadyMessageEvent(ReadyMessageEvent event) {
		try {
			String json = objectMapper.writeValueAsString(event.readyMessageRes());
			redisTemplate.convertAndSend(event.channel(), json);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("ReadyMessageRes 직렬화 실패", e);
		}
	}
}