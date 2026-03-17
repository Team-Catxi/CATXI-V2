package com.project.catxi.map.dto;

import java.util.List;

import com.project.catxi.common.domain.Location;

public record MapInfoRes(
	Location departure,
	List<CoordinateRes> coordinates
) { }
