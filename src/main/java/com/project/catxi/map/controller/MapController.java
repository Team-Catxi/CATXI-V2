package com.project.catxi.map.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.map.dto.CoordinateReq;
import com.project.catxi.map.dto.CoordinateRes;
import com.project.catxi.map.dto.DepartureReq;
import com.project.catxi.map.dto.MapInfoRes;
import com.project.catxi.map.service.MapService;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/map")
public class MapController {

	private final MapService mapService;
	public MapController(MapService mapService) {
		this.mapService = mapService;
	}

	@GetMapping("/{roomId}/coordinates")
	@Operation(summary = "채팅방 참가자들의 좌표를 조회합니다",
		description = "해당 채팅방에 참여중인 모든 유저의 좌표를 조회합니다. "
			+ "좌표는 레디스에 저장된 정보를 기반으로 하며, 5분 동안 자신의 좌표를 갱신하지 않은 참가자의 좌표는 반환되지 않습니다.")
	public ResponseEntity<ApiResponse<MapInfoRes>> getCoordinates(
		@PathVariable Long roomId
	){
		return ResponseEntity.ok(
			ApiResponse.success(mapService.getCoordinates(roomId))
		);
	}

	@PostMapping("/{roomId}/save-depart")
	@Operation(summary = "출발지 좌표 저장 API", description = "출발지 좌표를 레디스에 저장합니다. "
		+ "채팅방 생성 시 호출해주세요.")
	public ResponseEntity<ApiResponse<Void>> saveDepart(@PathVariable Long roomId, @RequestBody DepartureReq req){
		mapService.saveDepartureCoordinate( req.latitude(), req.longitude(), roomId);

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

}
