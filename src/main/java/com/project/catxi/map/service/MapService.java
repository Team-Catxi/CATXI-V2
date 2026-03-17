package com.project.catxi.map.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.map.dto.CoordinateReq;
import com.project.catxi.map.dto.CoordinateRes;
import com.project.catxi.map.dto.MapInfoRes;
import com.project.catxi.member.domain.Member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapService {

	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final CoordinateProvider coordinateProvider;

	public MapInfoRes getCoordinates(Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(chatRoom);

		List<CoordinateRes> coordinateRes = participants.stream()
			.map(participant -> {
				Member member = participant.getMember();
				Map<String, Double> coordMap = coordinateProvider.getCoordinate(roomId, member.getEmail());

				return new CoordinateRes(roomId, member.getEmail(),	member.getMembername(), member.getNickname(),
					getValueFromMap(coordMap, "latitude"),
					getValueFromMap(coordMap, "longitude"),
					getValueFromMap(coordMap, "distance"));
			})
			.toList();
		return new MapInfoRes(chatRoom.getStartPoint(), coordinateRes);
	}

	public void saveDepartureCoordinate(double latitude, double longitude, Long roomId) {
		coordinateProvider.saveDeparture(roomId, latitude, longitude);
	}

	public Double handleSaveCoordinateAndDistance(CoordinateReq coordinateReq) {
		Map<String, Double> departure = coordinateProvider.getDeparture(coordinateReq.roomId());

		Double distance = null;
		if (departure != null && departure.get("latitude") != null && departure.get("longitude") != null) {
			distance = calculateDistance(coordinateReq.latitude(), coordinateReq.longitude(),
				getValueFromMap(departure, "latitude"), getValueFromMap(departure, "longitude"));
		}

		coordinateProvider.saveCoordinateWithDistance(coordinateReq.roomId(), coordinateReq.email(),
				coordinateReq.latitude(), coordinateReq.longitude(), distance);
		return distance;
	}

	private Double getValueFromMap(Map<String, Double> map, String key) {
		return map != null ? map.get(key) : null;
	}

	private double calculateDistance (double lat1, double lon1, double lat2, double lon2) {
		final int R = 6371000;
		double latDistance = Math.toRadians(lat2 - lat1);
		double lonDistance = Math.toRadians(lon2 - lon1);
		double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
				* Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double distance = R * c;

		return Math.round((distance / 1000) * 100.0) / 100.0;
	}

}
