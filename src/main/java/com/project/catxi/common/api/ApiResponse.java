package com.project.catxi.common.api;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.project.catxi.common.api.error.ErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.success.CommonSuccessCode;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder({"success", "code", "message", "data"})
public class ApiResponse<T> {

	private final boolean success;
	private final String code;
	private final String message;
	private final T data;

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, CommonSuccessCode.SUCCESS.getCode(),
			CommonSuccessCode.SUCCESS.getMessage(), data);
	}

	public static ApiResponse<Void> successWithNoData() {
		return new ApiResponse<>(true, CommonSuccessCode.SUCCESS.getCode(),
			CommonSuccessCode.SUCCESS.getMessage(), null);
	}

	public static <T> ApiResponse<T> created(T data) {
		return new ApiResponse<>(true, CommonSuccessCode.CREATED.getCode(),
			CommonSuccessCode.CREATED.getMessage(), data);
	}

	public static ApiResponse<Void> createdWithNoData() {
		return new ApiResponse<>(true, CommonSuccessCode.CREATED.getCode(),
			CommonSuccessCode.CREATED.getMessage(), null);
	}

	public static ApiResponse<Void> error(MemberErrorCode memberErrorCode) {
		return new ApiResponse<>(false, memberErrorCode.getCode(), memberErrorCode.getMessage(), null);
	}

	public static ApiResponse<Void> error(MemberErrorCode memberErrorCode, String customMessage) {
		return new ApiResponse<>(false, memberErrorCode.getCode(), customMessage, null);
	}

	public static ApiResponse<?> createFail(ErrorCode errorCode) {
		return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
	}

	public static ApiResponse<?> createFail(ErrorCode errorCode, String message) {
		return new ApiResponse<>(false, errorCode.getCode(), message, null);
	}
}
