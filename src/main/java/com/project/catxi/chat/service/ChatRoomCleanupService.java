package com.project.catxi.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomCleanupService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatMessageRepository chatMessageRepository;

	@Transactional
	public void deleteExpiredChatRooms() {
		LocalDateTime now = LocalDateTime.now();
		List<ChatRoom> expiredRooms = chatRoomRepository.findByDepartAtBefore(now);

		if (!expiredRooms.isEmpty()) {
			log.info("💥 만료된 채팅방 {}개 삭제 시작", expiredRooms.size());
			for (ChatRoom expiredRoom : expiredRooms) {
				chatMessageRepository.deleteAllByChatRoom(expiredRoom);
			}
			chatRoomRepository.deleteAll(expiredRooms);
			log.info("✅ 만료된 채팅방 삭제 완료");
		} else {
			log.info("🔍 삭제할 채팅방 없음");
		}
	}
}

