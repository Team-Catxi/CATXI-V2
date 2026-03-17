package com.project.catxi.chat.dto;


import org.springframework.data.domain.Page;

import com.project.catxi.common.api.CommonPageResponse;


public record ChatRoomPageRes(
	Long myRoomId,
	CommonPageResponse<ChatRoomRes> rooms
){
	public static ChatRoomPageRes of(Page<ChatRoomRes> page, Long myRoomId) {
		return new ChatRoomPageRes(
			myRoomId,
			CommonPageResponse.of(page)
		);
	}
}
