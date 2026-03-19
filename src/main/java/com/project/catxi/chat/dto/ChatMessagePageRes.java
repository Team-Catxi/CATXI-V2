package com.project.catxi.chat.dto;

import java.util.List;

public record ChatMessagePageRes(
	List<ChatMessageRes> messages,
	Long nextCursor,
	boolean hasNext
) { }
