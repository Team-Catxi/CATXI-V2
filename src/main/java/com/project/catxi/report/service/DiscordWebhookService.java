package com.project.catxi.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.report.domain.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class DiscordWebhookService {

    @Value("${discord.webhook.url:}")
    private String webhookUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public void sendReportNotification(Report report) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Discord webhook URL이 설정되지 않아 알림을 전송하지 않습니다.");
            return;
        }
        try {
            String payload = createPayload(report);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(webhookUrl, entity, String.class);
            log.info("Discord 신고 알림 전송 성공. Report ID: {}", report.getId());
        } catch (Exception e) {
            log.error("Discord 웹훅 전송 실패. Report ID: {}. Error: {}", report.getId(), e.getMessage());
        }
    }

    private String createPayload(Report report) throws Exception {
        String content = String.format("""
                        🚨 **새로운 신고 접수** 🚨
                        ```
                        신고 ID      : %d
                        채팅방 ID    : %d
                        
                        --- 신고자 정보 ---
                        이름 (닉네임)  : %s (%s)
                        학번         : %s
                        
                        --- 피신고자 정보 ---
                        이름 (닉네임)  : %s (%s)
                        학번         : %s
                        
                        --- 신고 내용 ---
                        사유         : %s
                        접수 시각    : %s
                        ```""",
                report.getId(),
                report.getRoomId(),
                report.getReporter().getMembername(),
                report.getReporter().getNickname(),
                report.getReporter().getStudentNo(),
                report.getReportedMember().getMembername(),
                report.getReportedMember().getNickname(),
                report.getReportedMember().getStudentNo(),
                report.getReason(),
                report.getCreatedTime()
        );
        
        return objectMapper.writeValueAsString(new DiscordWebhookPayload(content));
    }

    private record DiscordWebhookPayload(String content) {
    }
}
