package com.project.catxi.chat.service;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.dto.ParticipantsUpdateMessage;
import com.project.catxi.chat.dto.ReadyMessageRes;
import com.project.catxi.chat.dto.RoomEventMessage;
import com.project.catxi.map.dto.CoordinateRes;

@Slf4j
@Service
public class RedisPubSubService implements MessageListener {
	private final SimpMessageSendingOperations messageTemplate;
	public final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;

    public RedisPubSubService(@Qualifier("chatPubSub") StringRedisTemplate stringRedisTemplate,
		SimpMessageSendingOperations messageTemplate, ObjectMapper objectMapper
		) {
		this.messageTemplate = messageTemplate;
		this.stringRedisTemplate = stringRedisTemplate;
		this.objectMapper = objectMapper;
    }


	@Override
	//pattern 에는 topic의 이름의 패턴이 담겨있고 이 패턴을 기반으로 다이나믹한 코딩
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
		String payload = new String(message.getBody(),StandardCharsets.UTF_8);
		log.info("[Redis 메시지 수신] channel: {}, payload: {}", channel, payload);

		try {
			if ("chat".equals(channel)) {
				// 채팅 메시지 처리 (WebSocket 브로드캐스트만)
				ChatMessageSendReq chatMessageDto = objectMapper.readValue(payload, ChatMessageSendReq.class);
				messageTemplate.convertAndSend("/topic/" + chatMessageDto.roomId(), chatMessageDto);
				
			} else if (channel.equals("map")) {
				CoordinateRes coordinateRes = objectMapper.readValue(payload, CoordinateRes.class);
				messageTemplate.convertAndSend("/topic/map/" + coordinateRes.roomId(), coordinateRes);
			} else if (channel.startsWith("ready:")) {
				ReadyMessageRes readyMessage = objectMapper.readValue(payload, ReadyMessageRes.class);
				messageTemplate.convertAndSend("/topic/ready/" + readyMessage.roomId(), readyMessage);
			} else if (channel.startsWith("participants:")) {
				ParticipantsUpdateMessage update = objectMapper.readValue(payload, ParticipantsUpdateMessage.class);
				messageTemplate.convertAndSend("/topic/room/" + update.roomId() + "/participants", update);
			} else if (channel.startsWith("kick:")) {
				String email = channel.split(":",2)[1];
				log.info("[강퇴 메시지 수신] channel: {}, 대상 이메일: {}", channel, email);
				log.info("[강퇴 메시지 전송 시도] 대상: {}, destination: /queue/kick, payload: KICKED", email);
				messageTemplate.convertAndSendToUser(email, "/queue/kick", "KICKED");
				log.info("[강퇴 메시지 전송 완료] 대상: {}", email);
			} else if (channel.startsWith("roomdeleted:")) {
				RoomEventMessage evt = objectMapper.readValue(payload, RoomEventMessage.class);
				messageTemplate.convertAndSend("/topic/room/" + evt.roomId() + "/deleted", evt);
			} else if(channel.startsWith("readyresult:")){
				RoomEventMessage evt = objectMapper.readValue(payload, RoomEventMessage.class);
				messageTemplate.convertAndSend("/topic/ready/" + evt.roomId() + "/result", evt);
			}
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
