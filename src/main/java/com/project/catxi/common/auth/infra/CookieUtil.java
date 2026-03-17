package com.project.catxi.common.auth.infra;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

  private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh";

  public static ResponseCookie createCookie(String token, Duration ttl) {

    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(ttl)
        .sameSite("None")
        .build();
  }

  public static ResponseCookie deleteCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(0) // 즉시 만료
        .sameSite("None")  // None 대신 Lax 사용
        .build();
  }
}
