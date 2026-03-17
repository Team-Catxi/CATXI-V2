package com.project.catxi.chat.dto;

import java.time.LocalDateTime;

import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;

public record RoomCreateRes(
	Long    roomId,
	Location startPoint,
	Location endPoint,
	Long recruitSize,
	LocalDateTime departAt,
	RoomStatus status
) { }