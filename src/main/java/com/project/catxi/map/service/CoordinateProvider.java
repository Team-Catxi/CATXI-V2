package com.project.catxi.map.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.common.api.error.MapErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CoordinateProvider {
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private static final long GEO_TTL_SECONDS = 300;

	public Map<String, Double> getCoordinate(Long roomId, String email) {
		String key = "map:" + roomId + ":" + email;
		String json = redisTemplate.opsForValue().get(key);

		if (json == null) {
			return null;
		}

		try{
			return objectMapper.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			log.warn("[좌표 파싱 실패] email: {}, roomId: {}", key, roomId);
			return null;
		}
	}

	public Map<String, Double> getDeparture(Long roomId) {
		String key = "map:" + roomId + ":" + "departure";
		String json = redisTemplate.opsForValue().get(key);

		if (json == null) {
			return null;
		}

		try{
			return objectMapper.readValue(json, new TypeReference<>() {});
		} catch (Exception e) {
			log.warn("[출발지 좌표 파싱 실패] roomId: {}", roomId);
			return null;
		}
	}

	public void saveCoordinateWithDistance(Long roomId, String email, Double latitude, Double longitude, Double distance) {
		String key = "map:" + roomId + ":" + email;
		Map<String, Double> coordMap = new HashMap<>();
		coordMap.put("latitude", latitude);
		coordMap.put("longitude", longitude);
		coordMap.put("distance", distance);

		try {
			String json = objectMapper.writeValueAsString(coordMap);
			redisTemplate.opsForValue().set(key, json, GEO_TTL_SECONDS, TimeUnit.SECONDS); // TTL 적용
		} catch (Exception e) {
			log.error("[좌표 저장 실패] email: {}, roomId: {}", email, roomId);
			throw new CatxiException(MapErrorCode.COORDINATE_SAVE_FAILED);
		}
	}

	public void saveDeparture(Long roomId, Double latitude, Double longitude) {
		String key = "map:" + roomId + ":" + "departure";
		Map<String, Object> coordMap = new HashMap<>();
		coordMap.put("latitude", latitude);
		coordMap.put("longitude", longitude);

		try {
			String json = objectMapper.writeValueAsString(coordMap);
			redisTemplate.opsForValue().set(key, json, Duration.ofHours(48)); //TTL 48시간
		}catch (Exception e) {
			throw new CatxiException(MapErrorCode.COORDINATE_SAVE_FAILED);
		}
	}


}
