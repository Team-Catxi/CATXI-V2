package com.project.catxi.chat.repository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.project.catxi.chat.dto.ChatRoomRes;
import com.project.catxi.common.domain.Location;

public interface ChatRoomRepositoryCustom {
	Page<ChatRoomRes> findByLocationAndDirection(Location location, String point, Pageable pageable);
}
