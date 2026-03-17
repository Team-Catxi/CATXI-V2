package com.project.catxi.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom room);

	void deleteAllByChatRoom(ChatRoom chatRoom);
	
	Optional<ChatMessage> findTopByChatRoomAndMemberOrderByCreatedTimeDesc(ChatRoom chatRoom, Member member);
}

