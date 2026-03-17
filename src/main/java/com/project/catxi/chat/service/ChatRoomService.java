package com.project.catxi.chat.service;


import static com.project.catxi.chat.domain.QChatRoom.*;


import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.domain.KickedParticipant;
import com.project.catxi.chat.dto.ChatRoomInfoRes;
import com.project.catxi.chat.dto.ChatRoomRes;
import com.project.catxi.chat.dto.ParticipantBrief;
import com.project.catxi.chat.dto.ParticipantsUpdateMessage;
import com.project.catxi.chat.dto.RoomCreateReq;
import com.project.catxi.chat.dto.RoomCreateRes;
import com.project.catxi.chat.dto.RoomDeletedEvent;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.chat.repository.KickedParticipantRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final MemberRepository memberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ObjectMapper objectMapper;
	private final ApplicationEventPublisher applicationEventPublisher;

	private final @Qualifier("chatPubSub") StringRedisTemplate stringRedisTemplate;

	private final KickedParticipantRepository kickedParticipantRepository;

	private final ChatMessageService chatMessageService;


	public RoomCreateRes createRoom(RoomCreateReq roomReq, String email) {
		Member host = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		HostNotInOtherRoom(host);

		if (roomReq.startPoint().equals(roomReq.endPoint())) {
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		}

		ChatRoom room = ChatRoom.builder()
			.host(host)
			.startPoint(roomReq.startPoint())
			.endPoint(roomReq.endPoint())
			.departAt(roomReq.departAt())
			.status(RoomStatus.WAITING)
			.maxCapacity(roomReq.recruitSize())
			.build();
		chatRoomRepository.save(room);

		ChatParticipant hostPart = ChatParticipant.builder()
			.chatRoom(room)
			.member(host)
			.isHost(true)
			.isReady(true)
			.build();
		chatParticipantRepository.save(hostPart);

		return new RoomCreateRes(
			room.getRoomId(),
			room.getStartPoint(),
			room.getEndPoint(),
			room.getMaxCapacity(),
			room.getDepartAt(),
			room.getStatus()
		);
	}


	public Page<ChatRoomRes> getChatRoomList(String direction, String station, String sort, Integer page) {
		Pageable pageable = PageRequest.of(page, 10, Sort.by(sort));

		Location location = switch (station) {
			case "SOSA_ST" -> Location.SOSA_ST;
			case "YEOKGOK_ST" -> Location.YEOKGOK_ST;
			case "ALL" -> null;
			default -> throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		};

		return chatRoomRepository.findByLocationAndDirection(location, direction, pageable);
	}


	public void leaveChatRoom(Long roomId, String email) {
		Member member = memberRepository.findByEmail(email).orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
		ChatParticipant chatParticipant = chatParticipantRepository
			.findByChatRoomAndMember(chatRoom, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
		if (chatParticipant.isHost()) {
			Long id = chatRoom.getRoomId();
			var emails = chatParticipantRepository.findParticipantEmailsByChatRoom(chatRoom);
			var hostNickname = member.getNickname();

			// 트랜잭션 커밋 후 Redis로 브로드캐스트되도록 이벤트 발행
			applicationEventPublisher.publishEvent(new RoomDeletedEvent(id, emails, hostNickname));

			chatMessageRepository.deleteAllByChatRoom(chatRoom);
			chatRoomRepository.delete(chatRoom);
			return;
		}

		chatParticipantRepository.delete(chatParticipant);

		sendParticipantUpdateMessage(chatRoom);

		String systemMessage = member.getNickname() + " 님이 퇴장하셨습니다.";
		chatMessageService.sendSystemMessage(roomId, systemMessage);
	}

	public void joinChatRoom(Long roomId, String email) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		if (kickedParticipantRepository.existsByChatRoomAndMember(chatRoom, member)) {
			throw new CatxiException(ChatParticipantErrorCode.BLOCKED_FROM_ROOM);
		}

		HostNotInOtherRoom(member);

		if(chatRoom.getStatus() != RoomStatus.WAITING)
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);

		long current = chatParticipantRepository.countByChatRoom(chatRoom);
		if (current >= chatRoom.getMaxCapacity() + 1)
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_FULL);

		ChatParticipant chatParticipant = ChatParticipant.builder()
			.chatRoom(chatRoom)
			.member(member)
			.build();

		chatParticipantRepository.save(chatParticipant);

		sendParticipantUpdateMessage(chatRoom);

		chatMessageService.sendSystemMessage(roomId, member.getNickname() + " 님이 입장하셨습니다.");
	}

	public Long getMyChatRoomId(String email) {
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByMember(member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
		Long myRoomId= participant.getChatRoom().getRoomId();
		return myRoomId;
	}

	public void kickUser(Long roomId, String requesterEmail, String targetEmail) {
		log.info("[강퇴 요청] roomId: {}, 요청자: {}, 대상자: {}", roomId, requesterEmail, targetEmail);
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member requester = memberRepository.findByEmail(requesterEmail)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		Member target = memberRepository.findByEmail(targetEmail)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		if (!room.getHost().equals(requester)) {
			throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
		}

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, target)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		log.info("[강퇴 시작] roomId: {}, 강퇴자: {}, 대상자: {}", roomId, requester.getEmail(), target.getEmail());

		chatParticipantRepository.delete(participant);
		log.info("[강퇴 참여자 삭제 완료] roomId: {}, 대상자: {}", roomId, target.getEmail());

		KickedParticipant kicked = KickedParticipant.builder()
			.chatRoom(room)
			.member(target)
			.build();
		kickedParticipantRepository.save(kicked);
		log.info("[강퇴 기록 저장 완료] roomId: {}, 대상자: {}", roomId, target.getEmail());

		sendParticipantUpdateMessage(room);
		log.info("[참여자 업데이트 메시지 전송 완료] roomId: {}", roomId);

		String msg = target.getNickname() + " 님이 강퇴되었습니다.";
		chatMessageService.sendSystemMessage(roomId, msg);
		log.info("[시스템 메시지 전송 완료] roomId: {}, message: {}", roomId, msg);

		String channel = "kick:" + target.getEmail();
		log.info("[강퇴 알림 채널 발행 시도] channel: {}, message: KICKED", channel);
		stringRedisTemplate.convertAndSend(channel, "KICKED");
		log.info("[강퇴 알림 채널 발행 완료] channel: {}, 대상자: {}", channel, target.getEmail());


	}


	private void HostNotInOtherRoom(Member host) {
		boolean exists = chatParticipantRepository.existsByMember(host);
		if (exists)
			throw new CatxiException(ChatParticipantErrorCode.ALREADY_IN_ROOM);

	}

	public boolean isHost(Long roomId, String email){
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
		return chatRoom.getHost().equals(member);
	}


	public boolean isRoomParticipant(String email, Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new EntityNotFoundException("room not found"));
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("member not found"));
		List<ChatParticipant> chatParticipants = chatParticipantRepository.findByChatRoom(chatRoom);
		for (ChatParticipant c : chatParticipants) {
			if (c.getMember().equals(member)) {
				return true;
			}
		}
		return false;

	}

	public void checkRoomEnter(Long roomId, String email) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
		if(!isRoomParticipant(email, roomId)){
			throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
		}

		if((chatRoom.getStatus()== RoomStatus.READY_LOCKED) && !isRoomParticipant(email, roomId)){
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_READY_LOCKED);
		}
	}

	public ChatRoomInfoRes getChatRoomInfo(Long roomId, String email) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		boolean isParticipant = isRoomParticipant(email, roomId);

		if (!isParticipant) {
			throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
		}

		List<String> participantEmails = chatParticipantRepository.findParticipantEmailsByChatRoom(chatRoom);
		List<String> participantNicknames = chatParticipantRepository.findParticipantNicknamesByChatRoom(chatRoom);

		return ChatRoomInfoRes.from(chatRoom, participantEmails, participantNicknames);

	}

	private void sendParticipantUpdateMessage(ChatRoom chatRoom) {
		List<String> nicknames = chatParticipantRepository.findParticipantNicknamesByChatRoom(chatRoom);
		List<String> emails    = chatParticipantRepository.findParticipantEmailsByChatRoom(chatRoom);

		int size = Math.min(nicknames.size(), emails.size());
		List<ParticipantBrief> participants = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			participants.add(new ParticipantBrief(nicknames.get(i), emails.get(i)));
		}

		ParticipantsUpdateMessage update =
			new ParticipantsUpdateMessage(chatRoom.getRoomId(), participants);

		try {
			String json = objectMapper.writeValueAsString(update);
			stringRedisTemplate.convertAndSend("participants:" + chatRoom.getRoomId(), json);
		} catch (Exception e) {
			throw new RuntimeException("참여자 목록 발행 실패", e);
		}
	}

}