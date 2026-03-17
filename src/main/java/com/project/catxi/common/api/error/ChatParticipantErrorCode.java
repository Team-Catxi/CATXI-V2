package com.project.catxi.common.api.error;

import org.springframework.boot.actuate.autoconfigure.observation.ObservationProperties;
import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatParticipantErrorCode implements ErrorCode{

	ALREADY_READY(HttpStatus.BAD_REQUEST, "CHATPARTICIPANT400", "이미 준비 상태입니다."),
	PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHATPARTICIPANT404", "채팅방 참여자를 찾을 수 없습니다."),
	ALREADY_IN_ROOM(HttpStatus.CONFLICT,"CHATPARTICIPANT409","이미 참여 중인 채팅방이 있습니다."),
	BLOCKED_FROM_ROOM(HttpStatus.BAD_REQUEST, "CHATPARTICIPANT403", "강퇴된 유저는 해당 채팅방에 재입장할 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
