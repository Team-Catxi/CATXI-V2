package com.project.catxi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(Arrays.asList(
            "https://catxi-university-taxi-b0936.web.app",
            "https://catxi.shop",
            "https://www.catxi.shop",
            "http://localhost:3000",
            "http://localhost:5173"
        ));

        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "access",
            "isNewUser",
            "X-Access-Token-Refreshed"
        ));

        // 쿠키(refresh token) 및 Authorization 헤더 허용
        configuration.setAllowCredentials(true);

        // Preflight 캐시 시간 (1시간)
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
