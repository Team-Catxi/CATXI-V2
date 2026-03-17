package com.project.catxi.chat.dto;

import java.time.LocalDateTime;

public record ChatMessageSendReq(
	Long roomId,
	String email,
	String message,
	LocalDateTime sentAt
) {
	public ChatMessageSendReq withRoomId(Long newRoomId) {
		return new ChatMessageSendReq(newRoomId, this.email, this.message, this.sentAt);
	}
}
