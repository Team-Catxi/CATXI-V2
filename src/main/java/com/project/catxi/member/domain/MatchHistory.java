package com.project.catxi.member.domain;

import com.project.catxi.common.domain.BaseTimeEntity;
import com.project.catxi.common.domain.Location;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDateTime;

import com.project.catxi.chat.domain.ChatRoom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class MatchHistory extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;


	// 이력의 주인 유저(조회의 기준이 되는 멤버)
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id",nullable = false)
	private Member user;

	//출발점
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Location startPoint;

	//도착지
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Location endPoint;

	//동행자
	@ElementCollection
	@CollectionTable(name = "matchHistory_fellas", joinColumns = @JoinColumn(name = "history_id"))
	@Column(name = "fellas", nullable = false)
	private List<String> fellas = new ArrayList<>();

	//매치 시점
	@Column(nullable = false)
	private LocalDateTime matchedAt;

	@Column(name = "created_at")
	private LocalDateTime createdAt;

	//사용일자 이력에 대한 얘기 필요
}
