package com.project.catxi.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.domain.KickedParticipant;
import com.project.catxi.member.domain.Member;

public interface KickedParticipantRepository extends JpaRepository<KickedParticipant, Long> {
	boolean existsByChatRoomAndMember(ChatRoom room, Member member);
}
