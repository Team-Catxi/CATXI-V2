
package com.project.catxi.chat.config;

import java.util.Collections;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;


import com.project.catxi.chat.service.ChatRoomService;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.jwt.JwtUtil;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompHandler implements ChannelInterceptor {

	private final JwtUtil jwtUtil;
	private final ChatRoomService chatRoomService;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

		if (StompCommand.CONNECT == accessor.getCommand()) {
			String token = extractToken(accessor);
			try {
				Claims claims = jwtUtil.parseJwt(token);
				jwtUtil.isExpired(claims);
				log.info("CONNECT - 토큰 유효성 검증 완료");

				String email = jwtUtil.getEmail(claims);
				accessor.setUser(new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList()));
				log.info("CONNECT - 토큰 유효성 검증 및 Principal 설정 완료: {}", email);

			} catch (Exception e) {
				throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
			}
		}

		if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
			final String dest = accessor.getDestination();
			if (dest == null) {
				throw new AuthenticationServiceException("destination 누락");
			}

			// 1) 개인 큐는 통과 (사용자 본인만 받음)
			if (dest.startsWith("/user/queue/")) {
				return message;
			}

			// 2) topic 계열만 방 권한 검사
			if (!dest.startsWith("/topic/")) {
				throw new AuthenticationServiceException("지원하지 않는 destination: " + dest);
			}

			// 인증 주체(email) 결정: Principal 우선, 없으면 JWT 재파싱
			final String email = resolveEmail(accessor);

			// 3) destination 어디에 있든 roomId(숫자 세그먼트)를 찾아서 권한 검사
			Long roomId = extractNumericSegment(dest);
			if (roomId == null) {
				throw new AuthenticationServiceException("destination에서 roomId를 찾을 수 없습니다: " + dest);
			}

			if (!chatRoomService.isRoomParticipant(email, roomId)) {
				log.warn("SUBSCRIBE 권한 없음: email={}, roomId={}, dest={}", email, roomId, dest);
				throw new AuthenticationServiceException("해당 room에 권한이 없습니다");
			}

			log.info("SUBSCRIBE 허용: email={}, roomId={}, dest={}", email, roomId, dest);
		}

		return message;
	}

	private String resolveEmail(StompHeaderAccessor accessor) {
		// 1) Principal 우선
		if (accessor.getUser() != null && accessor.getUser().getName() != null) {
			return accessor.getUser().getName();
		}
		// 2) SUBSCRIBE 헤더로 온 JWT (프론트가 매 프레임 Authorization을 넣는 경우)
		String bearerToken = accessor.getFirstNativeHeader("Authorization");
		if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
			Claims claims = jwtUtil.parseJwt(bearerToken.substring(7).trim());
			return jwtUtil.getEmail(claims);
		}
		// 3) 둘 다 없으면 인증정보 없음
		throw new AuthenticationServiceException("인증 정보가 없습니다(Principal/Authorization)");
	}

	private Long extractNumericSegment(String dest) {
		String[] segs = dest.split("/");
		for (int i = segs.length - 1; i >= 0; i--) {
			String seg = segs[i];
			if (!seg.isEmpty() && seg.chars().allMatch(Character::isDigit)) {
				try { return Long.valueOf(seg); } catch (NumberFormatException ignored) {}
			}
		}
		return null;
	}


	private String extractToken(StompHeaderAccessor accessor) {
		String bearerToken = accessor.getFirstNativeHeader("Authorization");
		if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
			throw new AuthenticationServiceException("Authorization 헤더가 잘못되었습니다");
		}
		return bearerToken.substring(7).trim();
	}
}

