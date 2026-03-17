package com.project.catxi.fcm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * - 애플리케이션 시작 시 Consumer Group 생성
 * - 주기적인 Stream 크기 관리 (Trim)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamsInitializer implements ApplicationRunner {

    private static final String STREAM_KEY = "fcm:stream";
    private static final String CONSUMER_GROUP = "fcm-consumers";
    private static final String DLQ_STREAM_KEY = "fcm:dlq";
    private static final long MAX_STREAM_LENGTH = 10000L; // 최대 메시지 수

    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            initializeStream(STREAM_KEY, CONSUMER_GROUP);
            initializeStream(DLQ_STREAM_KEY, CONSUMER_GROUP + "-dlq");
            log.info("✅ Redis Streams 초기화 완료");
        } catch (Exception e) {
            log.error("❌ Redis Streams 초기화 실패", e);
        }
    }

    /**
     * 스트림 및 Consumer Group 초기화
     * - ReadOffset.from("0"): 스트림 처음부터 읽기 설정
     */
    private void initializeStream(String streamKey, String groupName) {
        try {
            // Consumer Group 생성 (처음부터 읽기)
            redisTemplate.opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), groupName);

            log.info("Redis Consumer Group 생성 완료: stream={}, group={}",
                    streamKey, groupName);

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Redis Consumer Group 이미 존재: stream={}, group={}",
                        streamKey, groupName);
            } else if (e.getMessage() != null && e.getMessage().contains("requires the key to exist")) {
                log.info("스트림이 없어서 생성 중: {}", streamKey);
                createStreamWithDummyMessage(streamKey, groupName);
            } else {
                log.error("Redis Consumer Group 생성 실패: stream={}, group={}",
                        streamKey, groupName, e);
            }
        }
    }

    /**
     * 더미 메시지로 스트림 생성
     */
    private void createStreamWithDummyMessage(String streamKey, String groupName) {
        try {
            redisTemplate.opsForStream()
                    .add(streamKey, java.util.Map.of("init", "true"));

            redisTemplate.opsForStream()
                    .createGroup(streamKey, ReadOffset.from("0"), groupName);

            log.info("더미 메시지로 스트림 및 그룹 생성 완료: stream={}, group={}",
                    streamKey, groupName);

        } catch (Exception e) {
            log.error("더미 메시지 생성 실패: stream={}", streamKey, e);
        }
    }

    /**
     * Stream 크기 제한 (XTRIM)
     * - 1시간마다 실행
     * - 최대 10000개 메시지 유지
     */
    @Scheduled(fixedDelay = 3600000) // 1시간마다
    public void trimStreams() {
        try {
            Long trimmedMain = redisTemplate.opsForStream()
                    .trim(STREAM_KEY, MAX_STREAM_LENGTH);
            Long trimmedDlq = redisTemplate.opsForStream()
                    .trim(DLQ_STREAM_KEY, MAX_STREAM_LENGTH);

            if (trimmedMain > 0 || trimmedDlq > 0) {
                log.info("Redis Stream Trim 완료: main={}, dlq={}", trimmedMain, trimmedDlq);
            }

        } catch (Exception e) {
            log.error("Redis Stream Trim 실패", e);
        }
    }
}
