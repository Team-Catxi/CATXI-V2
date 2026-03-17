package com.project.catxi.chat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.project.catxi.common.domain.BaseTimeEntity;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.Member;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long roomId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "host_id")
	private Member host;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Location startPoint;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Location endPoint;

	@Column(nullable = false)
	private LocalDateTime departAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	@Setter
	private RoomStatus status;

	@Column(nullable = false)
	private Long maxCapacity;   // 1~4

	@OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
	private final List<ChatParticipant> participants = new ArrayList<>();

	@OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<KickedParticipant> kickedParticipants = new ArrayList<>();

	@Column(nullable = true)
	private LocalDateTime matchedAt;

	//matched된 시점 기록
	//matched되었다 다시 waiting으로 되돌아갈 수 있는가?
	public void matchedStatus(RoomStatus newStatus) {
		if (status == RoomStatus.READY_LOCKED && this.matchedAt == null) {
			this.matchedAt = LocalDateTime.now();
		}
		this.status = newStatus;
	}

}

