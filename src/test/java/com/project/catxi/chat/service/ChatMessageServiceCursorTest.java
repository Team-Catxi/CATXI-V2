package com.project.catxi.chat.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.ChatMessagePageRes;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.fcm.repository.FcmOutboxRepository;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatMessageServiceCursorTest {

	@InjectMocks
	private ChatMessageService chatMessageService;

	@Mock private ChatRoomRepository chatRoomRepository;
	@Mock private MemberRepository memberRepository;
	@Mock private ChatMessageRepository chatMessageRepository;
	@Mock private ChatParticipantRepository chatParticipantRepository;
	@Mock private FcmOutboxRepository fcmOutboxRepository;
	@Mock private ObjectMapper objectMapper;
	@Mock private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

	private ChatRoom room;
	private Member member;

	@BeforeEach
	void setUp() {
		room = mock(ChatRoom.class);
		given(room.getRoomId()).willReturn(1L);

		member = mock(Member.class);
		given(member.getId()).willReturn(1L);
		given(member.getEmail()).willReturn("test@test.com");
		given(member.getNickname()).willReturn("tester");

		given(memberRepository.findByEmail("test@test.com")).willReturn(Optional.of(member));
		given(chatRoomRepository.findById(1L)).willReturn(Optional.of(room));
		given(chatParticipantRepository.existsByChatRoomAndMember(room, member)).willReturn(true);
	}

	@Test
	@DisplayName("cursor 없이 요청하면 최신 메시지 size개를 ASC 순서로 반환한다")
	void getChatHistory_firstPage() {
		// given: id=5,4,3 (DESC) 3개 반환 → size=3이면 hasNext=false
		List<ChatMessage> dbResult = buildMessages(List.of(5L, 4L, 3L));
		given(chatMessageRepository.findByChatRoomOrderByIdDesc(eq(room), any(PageRequest.class)))
				.willReturn(dbResult);

		// when
		ChatMessagePageRes res = chatMessageService.getChatHistory(1L, "test@test.com", null, 3);

		// then
		assertThat(res.hasNext()).isFalse();
		assertThat(res.nextCursor()).isNull();
		assertThat(res.messages()).hasSize(3);
		// ASC 순서 확인 (3 → 4 → 5)
		assertThat(res.messages().get(0).messageId()).isEqualTo(3L);
		assertThat(res.messages().get(2).messageId()).isEqualTo(5L);
	}

	@Test
	@DisplayName("size+1개 반환되면 hasNext=true이고 nextCursor는 마지막 페이지의 첫 번째 messageId이다")
	void getChatHistory_hasNextPage() {
		// given: id=5,4,3,2 (DESC, size+1=4개) → size=3
		List<ChatMessage> dbResult = buildMessages(List.of(5L, 4L, 3L, 2L));
		given(chatMessageRepository.findByChatRoomOrderByIdDesc(eq(room), any(PageRequest.class)))
				.willReturn(dbResult);

		// when
		ChatMessagePageRes res = chatMessageService.getChatHistory(1L, "test@test.com", null, 3);

		// then
		assertThat(res.hasNext()).isTrue();
		assertThat(res.messages()).hasSize(3);
		// subList(0,3) = [5,4,3] → reverse → [3,4,5]
		// nextCursor = reverse 후 첫 번째 = id 3
		assertThat(res.nextCursor()).isEqualTo(3L);
	}

	@Test
	@DisplayName("cursor 전달 시 해당 id 미만 메시지를 ASC 순서로 반환한다")
	void getChatHistory_withCursor() {
		// given: cursor=5 → id<5 중 id=4,3 반환 (size=3이지만 2개만 존재 → hasNext=false)
		List<ChatMessage> dbResult = buildMessages(List.of(4L, 3L));
		given(chatMessageRepository.findByChatRoomAndIdLessThanOrderByIdDesc(eq(room), eq(5L), any(PageRequest.class)))
				.willReturn(dbResult);

		// when
		ChatMessagePageRes res = chatMessageService.getChatHistory(1L, "test@test.com", 5L, 3);

		// then
		assertThat(res.hasNext()).isFalse();
		assertThat(res.nextCursor()).isNull();
		assertThat(res.messages()).hasSize(2);
		assertThat(res.messages().get(0).messageId()).isEqualTo(3L);
		assertThat(res.messages().get(1).messageId()).isEqualTo(4L);
	}

	@Test
	@DisplayName("메시지가 없으면 빈 리스트와 hasNext=false를 반환한다")
	void getChatHistory_emptyRoom() {
		// given
		given(chatMessageRepository.findByChatRoomOrderByIdDesc(eq(room), any(PageRequest.class)))
				.willReturn(List.of());

		// when
		ChatMessagePageRes res = chatMessageService.getChatHistory(1L, "test@test.com", null, 50);

		// then
		assertThat(res.messages()).isEmpty();
		assertThat(res.hasNext()).isFalse();
		assertThat(res.nextCursor()).isNull();
	}

	private List<ChatMessage> buildMessages(List<Long> ids) {
		List<ChatMessage> list = new ArrayList<>();
		for (Long id : ids) {
			ChatMessage msg = mock(ChatMessage.class);
			given(msg.getId()).willReturn(id);
			given(msg.getMember()).willReturn(member);
			given(msg.getContent()).willReturn("msg" + id);
			given(msg.getCreatedTime()).willReturn(null);
			list.add(msg);
		}
		return list;
	}
}
