package com.project.catxi.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.catxi.chat.service.ChatRoomService;
import com.project.catxi.chat.service.ReadyService;
import com.project.catxi.common.api.ApiResponse;

import com.project.catxi.member.dto.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ready")
public class ReadyController {
	private final ChatRoomService chatRoomService;
	private final ReadyService readyService;


	// 클라이언트로부터 메시지를 받아 해당 채팅방에 있는 모든 클라이언트에게 전송하는 엔드포인트
	@PostMapping("/request/{roomId}")
	public ResponseEntity<ApiResponse<Void>> sendMessage(@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) throws JsonProcessingException {
		readyService.requestReady(roomId, userDetails.getUsername());

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

	// 클라이언트로부터 메시지를 받아 해당 채팅방의 방장에게 전송하는 엔드포인트
	@PostMapping("/accept/{roomId}")
	public ResponseEntity<ApiResponse<Void>> sendMessageToHost(@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) throws JsonProcessingException {
		readyService.acceptReady(roomId, userDetails.getUsername());

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

	@PostMapping("/reject/{roomId}")
	public ResponseEntity<ApiResponse<Void>> rejectReady(@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) throws JsonProcessingException {
		readyService.rejectReady(roomId, userDetails.getUsername());
		chatRoomService.leaveChatRoom(roomId, userDetails.getUsername());

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}


}