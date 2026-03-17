package com.project.catxi.chat.service;

import static com.project.catxi.chat.domain.QChatRoom.*;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCleaner {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository chatMessageRepository;

	private final ChatRoomCleanupService cleanupService;

	@Scheduled(cron = "0 24 * * * *", zone = "Asia/Seoul")
	public void runCleanup() {
		log.info("🧹 [ChatRoomCleaner] 스케줄러 실행됨");
		cleanupService.deleteExpiredChatRooms(); // ✨ 트랜잭션 적용 메서드 호출

	}
}