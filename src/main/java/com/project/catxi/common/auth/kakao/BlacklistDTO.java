package com.project.catxi.common.auth.kakao;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BlacklistDTO (){

    @Schema(description = "블랙리스트 조회 응답")
    public record checkBlacklist(
        @Schema(description = "블랙리스트 등록 여부", example = "true")
        boolean isBlacklisted,
        
        @Schema(description = "사용자 ID", example = "1")
        Long userId,
        
        @Schema(description = "사용자 이메일", example = "user@example.com")
        String email
    ) {}
}