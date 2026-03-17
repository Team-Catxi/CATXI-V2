package com.project.catxi.chat.dto;

import java.time.LocalDateTime;

import com.project.catxi.common.domain.Location;

public record RoomCreateReq(Location startPoint,
							Location endPoint,
							Long recruitSize,
							LocalDateTime departAt)
{
}

