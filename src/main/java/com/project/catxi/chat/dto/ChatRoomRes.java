package com.project.catxi.chat.dto;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;

public record ChatRoomRes (
	Long roomId,
	Long hostId,
	String hostName,
	String hostNickname,
	Integer matchCount,
	Location startPoint,
	Location endPoint,
	Long recruitSize,
	Long currentSize,
	RoomStatus status,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
	LocalDateTime departAt,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
	LocalDateTime createdTime
){
	public static ChatRoomRes from (ChatRoom chatRoom){
		return new ChatRoomRes(
			chatRoom.getRoomId(),
			chatRoom.getHost().getId(),
			chatRoom.getHost().getMembername(),
			chatRoom.getHost().getNickname(),
			chatRoom.getHost().getMatchCount(),
			chatRoom.getStartPoint(),
			chatRoom.getEndPoint(),
			chatRoom.getMaxCapacity(),
			(long)chatRoom.getParticipants().size(),
			chatRoom.getStatus(),
			chatRoom.getDepartAt(),
			chatRoom.getCreatedTime()
		);
	}
}
