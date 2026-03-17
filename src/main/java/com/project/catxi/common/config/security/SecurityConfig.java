package com.project.catxi.common.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  
  private final JwtFilterConfig jwtFilterConfig;
  private final CorsConfigurationSource corsConfigurationSource;
  
  public SecurityConfig(JwtFilterConfig jwtFilterConfig, CorsConfigurationSource corsConfigurationSource) {
    this.jwtFilterConfig = jwtFilterConfig;
    this.corsConfigurationSource = corsConfigurationSource;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    //csrf disable
    http
        .csrf((auth) -> auth.disable());

    //기본 Form 로그인 방식 disable -> Custom 인증 로직 사용
    http
        .formLogin((auth) -> auth.disable());

    //http basic 인증 방식 disable (JWT 사용 필수)
    http
        .httpBasic((auth) -> auth.disable());

    //세션관리 - JWT 사용으로 무상태성
    http
        .sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

    //경로별 인가 작업
    http
        .authorizeHttpRequests((auth)-> auth
            .requestMatchers("/swagger", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll() // Swagger 허용
            .requestMatchers("/connect/**").permitAll()
            .requestMatchers("api/auth/login/kakao").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            //admin 권한 제어
            .requestMatchers("/api/admin/**").hasRole("ADMIN")

            .anyRequest().authenticated()
        );

    //cors 설정 (origin 명시적 허용)
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource));

    //JwtFilter
    jwtFilterConfig.configureJwtFilters(http);

    return http.build();
  }
}
