package com.project.catxi.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant,Long> {

    boolean existsByMember(Member member);

    List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom room);

    boolean existsByChatRoomAndMember(ChatRoom room, Member member);


    Optional<ChatParticipant> findByChatRoomAndMember(ChatRoom room, Member member);


    long countByChatRoom(ChatRoom room);

    long countByChatRoomAndIsReady(ChatRoom chatRoom, boolean ready);

    List<ChatParticipant> findByChatRoom(ChatRoom chatRoom);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE ChatParticipant cp SET cp.isReady = false WHERE cp.chatRoom.roomId = :roomId AND cp.isHost = false")
    void updateIsReadyFalseExceptHost(Long roomId);

    @Transactional
    void deleteAllByChatRoomAndIsReadyFalse(ChatRoom chatroom);

    @Query("SELECT cp.member.nickname FROM ChatParticipant cp WHERE cp.chatRoom = :chatRoom AND cp.isReady = false")
    List<String> findNickNameByChatRoomAndIsReadyFalse(ChatRoom chatRoom);

    Optional<ChatParticipant> findByMember(Member member);

    @Query("SELECT cp.member.email FROM ChatParticipant cp WHERE cp.chatRoom = :chatRoom")
    List<String> findParticipantEmailsByChatRoom(ChatRoom chatRoom);

    @Query("SELECT cp.member.nickname FROM ChatParticipant cp WHERE cp.chatRoom = :chatRoom")
    List<String> findParticipantNicknamesByChatRoom(ChatRoom chatRoom);

}
