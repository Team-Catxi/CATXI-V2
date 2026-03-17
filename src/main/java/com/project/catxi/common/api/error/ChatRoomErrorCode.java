package com.project.catxi.common.api.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatRoomErrorCode implements ErrorCode{

	CHATROOM_READY_LOCKED(HttpStatus.BAD_REQUEST, "CHATROOM400", "채팅방이 준비 요청 상태 입니다."),
	CHATROOM_NOT_READY_LOCKED(HttpStatus.BAD_REQUEST, "CHATROOM400", "채팅방이 준비 요청 상태가 아닙니다."),
	CHATROOM_NOT_WAITING(HttpStatus.BAD_REQUEST, "CHATROOM400", "채팅방이 대기 상태가 아닙니다."),
	CHATROOM_NOT_MATCHED(HttpStatus.NOT_FOUND, "CHATROOM404", "채팅방이 매칭 상태가 아닙니다."),
	CHATROOM_FULL(HttpStatus.NOT_FOUND, "CHATROOM405", "채팅방에 인원이 꽉찼습니다."),
	CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHATROOM404", "채팅방를 찾을 수 없습니다."),
	NOT_OWNED_CHATROOM(HttpStatus.FORBIDDEN, "CHATROOM403", "본인 소유의 채팅방이 아닙니다."),
	INVALID_CHATROOM_PARAMETER(HttpStatus.BAD_REQUEST, "CHATROOM400", "채팅방 요청 데이터가 올바르지 않습니다."),
	NOT_HOST(HttpStatus.NOT_FOUND,"CHATROOM403", "방장만 실행할 수 있는 명령입니다");


	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
