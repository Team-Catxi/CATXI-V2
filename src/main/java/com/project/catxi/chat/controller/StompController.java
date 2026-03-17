package com.project.catxi.chat.controller;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.service.ChatMessageService;
import com.project.catxi.chat.service.RedisPubSubService;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.member.repository.MemberRepository;

import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;
import com.project.catxi.map.dto.CoordinateReq;
import com.project.catxi.map.dto.CoordinateRes;
import com.project.catxi.map.service.MapService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
public class StompController {

	private final SimpMessageSendingOperations messageTemplate;
	private final ChatMessageService chatMessageService;
	private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final MapService mapService;
	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final MemberRepository memberRepository;


	@MessageMapping("/{roomId}")
	public void sendMessage(@DestinationVariable Long roomId, ChatMessageSendReq chatMessageSendReq) throws JsonProcessingException {
		chatMessageService.saveMessage(roomId, chatMessageSendReq);
		
		ChatMessageSendReq enriched = new ChatMessageSendReq(
			chatMessageSendReq.roomId(),
			chatMessageSendReq.email(),
			chatMessageSendReq.message(),
			LocalDateTime.now()
		);
		String message = objectMapper.writeValueAsString(enriched);
		
		// 채팅용 (모든 서버에서 수신하여 WebSocket 브로드캐스트)
		redisTemplate.convertAndSend("chat", message);
	}

	@MessageMapping("/map/{roomId}")
	public void sendCoordinate(@DestinationVariable Long roomId, CoordinateReq coordinateReq) throws JsonProcessingException {
		// 강퇴된 사용자 검증 - ChatParticipant 테이블에서 직접 확인
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
		Member member = memberRepository.findByEmail(coordinateReq.email())
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		if (!chatParticipantRepository.existsByChatRoomAndMember(room, member)) {
			throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
		}
		Double distance = mapService.handleSaveCoordinateAndDistance(coordinateReq);

		CoordinateRes enriched = new CoordinateRes(
			coordinateReq.roomId(),
			coordinateReq.email(),
			coordinateReq.name(),
			coordinateReq.nickname(),
			coordinateReq.latitude(),
			coordinateReq.longitude(),
			distance
		);
		String message = objectMapper.writeValueAsString(enriched);
		redisTemplate.convertAndSend("map", message);
	}
}
