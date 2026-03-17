package com.project.catxi.chat.dto;

public record KickRequest(
	Long roomId,
	String targetEmail
) {
}
