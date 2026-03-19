package com.project.catxi.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom room);

	@Query("SELECT m FROM ChatMessage m WHERE m.chatRoom = :room ORDER BY m.id DESC")
	List<ChatMessage> findByChatRoomOrderByIdDesc(@Param("room") ChatRoom room, Pageable pageable);

	@Query("SELECT m FROM ChatMessage m WHERE m.chatRoom = :room AND m.id < :cursor ORDER BY m.id DESC")
	List<ChatMessage> findByChatRoomAndIdLessThanOrderByIdDesc(
		@Param("room") ChatRoom room,
		@Param("cursor") Long cursor,
		Pageable pageable);

	void deleteAllByChatRoom(ChatRoom chatRoom);

	Optional<ChatMessage> findTopByChatRoomAndMemberOrderByCreatedTimeDesc(ChatRoom chatRoom, Member member);
}

