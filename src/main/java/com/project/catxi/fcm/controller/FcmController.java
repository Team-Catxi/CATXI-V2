package com.project.catxi.fcm.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.fcm.dto.FcmTokenUpdateReq;
import com.project.catxi.fcm.dto.FcmTokenUpdateRes;
import com.project.catxi.fcm.dto.FcmActiveStatusReq;
import com.project.catxi.fcm.service.FcmTokenService;
import com.project.catxi.fcm.service.FcmActiveStatusService;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.member.domain.Member;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FcmController {

    private final FcmTokenService fcmTokenService;
    private final FcmActiveStatusService fcmActiveStatusService;
    private final MemberRepository memberRepository;

    @PutMapping("/token")
    public ResponseEntity<ApiResponse<FcmTokenUpdateRes>> updateFcmToken(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FcmTokenUpdateReq request) {
        
        String email = userDetails.getUsername();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        FcmTokenUpdateRes response = fcmTokenService.updateFcmToken(member, request);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/active-status")
    public ResponseEntity<ApiResponse<Void>> updateActiveStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FcmActiveStatusReq request) {
        
        String email = userDetails.getUsername();
        fcmActiveStatusService.updateUserActiveStatus(email, request.roomId(), request.isActive());
        
        return ResponseEntity.ok(ApiResponse.successWithNoData());
    }

    @DeleteMapping("/token")
    public ResponseEntity<ApiResponse<Void>> deleteFcmToken(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        String email = userDetails.getUsername();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        
        fcmTokenService.deleteFcmToken(member);
        
        return ResponseEntity.ok(ApiResponse.successWithNoData());
    }

}