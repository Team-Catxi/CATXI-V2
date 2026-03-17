package com.project.catxi.chat.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.project.catxi.common.domain.ReadyType;
import com.project.catxi.member.domain.Member;

public record ReadyMessageRes(
	ReadyType type, // ex) "READY_REQUEST", "READY_ACCEPT", ...
	Long roomId,
	Long senderId,
	String senderEmail,
	String senderName,
	String content, // ex) "준비 상태로 변경되었습니다."
	LocalDateTime sentAt // ex) "2023-10-01T12:00:00"
) {
	public static ReadyMessageRes readyRequest(Long roomId, Member sender) {
		return new ReadyMessageRes(
			ReadyType.READY_REQUEST,
			roomId,
			sender.getId(),
			sender.getEmail(),
			sender.getMembername(),
			"방장이 Ready 요청을 보냈습니다",
			LocalDateTime.now()
		);
	}

	public static ReadyMessageRes readyAccept(Long roomId, Member sender) {
		return new ReadyMessageRes(
			ReadyType.READY_ACCEPT,
			roomId,
			sender.getId(),
			sender.getEmail(),
			sender.getMembername(),
			"참여자가 Ready를 수락했습니다",
			LocalDateTime.now()
		);
	}

	public static ReadyMessageRes readyDeny(Long roomId, Member sender) {
		return new ReadyMessageRes(
			ReadyType.READY_DENY,
			roomId,
			sender.getId(),
			sender.getEmail(),
			sender.getMembername(),
			"참여자가 Ready를 거절했습니다",
			LocalDateTime.now()
		);
	}
}
