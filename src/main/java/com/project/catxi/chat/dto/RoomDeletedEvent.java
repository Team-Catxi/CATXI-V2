package com.project.catxi.chat.dto;

import java.util.List;

public record RoomDeletedEvent(Long roomId, List<String> emails, String hostNickname) {}

