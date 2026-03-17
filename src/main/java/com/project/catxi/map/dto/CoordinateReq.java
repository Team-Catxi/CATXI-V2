package com.project.catxi.map.dto;

public record CoordinateReq(
	Long roomId,
	String email,
	String name,
	String nickname,
	Double latitude,
	Double longitude,
	Double distance
) { }
