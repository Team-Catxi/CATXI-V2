package com.project.catxi.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${fcm.service-account-file:}")
    private String serviceAccountFilePath;

    private FirebaseApp firebaseApp;

    @PostConstruct
    public void initialize() {
        try {
            if (serviceAccountFilePath.isEmpty()) {
                log.warn("FCM 서비스 계정 파일 경로가 설정되지 않았습니다. FCM 기능을 사용할 수 없습니다.");
                return;
            }

            InputStream serviceAccountStream;
            
            if (serviceAccountFilePath.startsWith("classpath:")) {
                String resourcePath = serviceAccountFilePath.substring("classpath:".length());
                serviceAccountStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
                if (serviceAccountStream == null) {
                    log.error("클래스패스에서 FCM 서비스 계정 파일을 찾을 수 없습니다: {}", resourcePath);
                    return;
                }
                log.info("클래스패스에서 FCM 서비스 계정 파일 로드: {}", resourcePath);
            } else {
                File file = new File(serviceAccountFilePath);
                if (!file.exists()) {
                    log.error("FCM 서비스 계정 파일을 찾을 수 없습니다: {}", serviceAccountFilePath);
                    return;
                }
                serviceAccountStream = Files.newInputStream(file.toPath());
                log.info("절대 경로에서 FCM 서비스 계정 파일 로드: {}", serviceAccountFilePath);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .build();

            if (FirebaseApp.getApps().isEmpty()) {
                this.firebaseApp = FirebaseApp.initializeApp(options);
                log.info("FirebaseApp 초기화 성공");
            } else {
                this.firebaseApp = FirebaseApp.getInstance();
            }
        } catch (Exception e) {
            log.error("FirebaseApp 초기화 실패: {}", e.getMessage());
            this.firebaseApp = null;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (firebaseApp == null) {
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }
    
    @PreDestroy
    public void cleanup() {
        try {
            if (firebaseApp != null) {
                firebaseApp.delete();
                log.info("FirebaseApp 정리 완료");
            }
        } catch (Exception e) {
            log.warn("FirebaseApp 정리 중 오류 발생: {}", e.getMessage());
        }
    }
}