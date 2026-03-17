package com.project.catxi.chat.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.domain.QChatParticipant;
import com.project.catxi.chat.domain.QChatRoom;
import com.project.catxi.chat.dto.ChatRoomRes;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.QMember;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryCustomImpl implements ChatRoomRepositoryCustom {
	private final JPAQueryFactory jpaQueryFactory;
	private static final Set<Location> STATIONS = Set.of(Location.SOSA_ST, Location.GURO_ST, Location.YEOKGOK_ST, Location.BUCHEON_ST, Location.SINDORIM_ST);

	@Override
	public Page<ChatRoomRes> findByLocationAndDirection(Location location, String point, Pageable pageable) {
		QChatRoom chatRoom = QChatRoom.chatRoom;
		QChatParticipant participant = QChatParticipant.chatParticipant;
		QMember host = QMember.member;

		// 1. 페이징 조회
		List<ChatRoomRes> chatRooms = jpaQueryFactory
			.select(Projections.constructor(
				ChatRoomRes.class,
				chatRoom.roomId,
				host.id,
				host.membername,
				host.nickname,
				host.matchCount,
				chatRoom.startPoint,
				chatRoom.endPoint,
				chatRoom.maxCapacity,
				participant.id.countDistinct(),
				chatRoom.status,
				chatRoom.departAt,
				chatRoom.createdTime
			))
			.from(chatRoom)
			.join(chatRoom.host, host)
			.leftJoin(chatRoom.participants, participant)
			.where(
				chatRoom.status.eq(RoomStatus.WAITING),
				filterByLocationAndPoint(location, point)
			)
			.groupBy(chatRoom.roomId)
			.orderBy(getOrderSpecifier(pageable.getSort()))
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();

		// 2. 전체 데이터 개수 조회
		JPAQuery<Long> total = jpaQueryFactory
			.select(chatRoom.count())
			.from(chatRoom)
			.where(
				chatRoom.status.eq(RoomStatus.WAITING),
				filterByLocationAndPoint(location, point)
			);

		return PageableExecutionUtils.getPage(
			chatRooms,
			pageable,
			total::fetchOne
		);
	}

	private OrderSpecifier<?>[] getOrderSpecifier(Sort sort) {
		List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();

		for (Sort.Order order : sort) {
			Order direction = order.isAscending() ? Order.ASC : Order.DESC;
			PathBuilder<ChatRoom> pathBuilder = new PathBuilder<>(ChatRoom.class, "chatRoom");
			orderSpecifiers.add(new OrderSpecifier<>(direction, pathBuilder.getString(order.getProperty())));
		}

		return orderSpecifiers.toArray(new OrderSpecifier[0]);
	}

	private BooleanExpression filterByLocationAndPoint(Location location, String point) {
		QChatRoom chatRoom = QChatRoom.chatRoom;
		//ALL(null)일 경우 TO_SCHOOL은 역 -> 학교, FROM_SCHOOL은 학교 -> 역
		if (point.equals("TO_SCHOOL")) {
			return location == null ? chatRoom.startPoint.in(STATIONS) :chatRoom.startPoint.eq(location);
		} else if (point.equals("FROM_SCHOOL")) {
			return location == null ? chatRoom.endPoint.in(STATIONS) : chatRoom.endPoint.eq(location);
		} else {
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		}
	}


}
