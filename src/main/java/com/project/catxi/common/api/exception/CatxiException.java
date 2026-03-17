package com.project.catxi.common.api.exception;

import com.project.catxi.common.api.error.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CatxiException extends RuntimeException {

	private final ErrorCode errorCode;
}
