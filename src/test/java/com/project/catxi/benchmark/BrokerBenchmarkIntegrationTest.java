package com.project.catxi.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.kafka.ChatKafkaConsumer;
import com.project.catxi.chat.kafka.ChatKafkaPublisher;
import com.project.catxi.chat.kafka.KafkaConfig;
import com.project.catxi.chat.service.RedisPubSubService;
import com.project.catxi.common.config.RedisConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.Message;
import org.springframework.messaging.core.MessagePostProcessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 3 — Spring 서비스 코드 레벨 브로커 성능 비교
 *
 * 측정 경로:
 *   Redis: redisTemplate.convertAndSend("chat", json)
 *          → RedisPubSubService.onMessage()
 *          → LatencyRecorder.convertAndSend()
 *
 *   Kafka: ChatKafkaPublisher.publish()
 *          → ChatKafkaConsumer.onMessage()
 *          → LatencyRecorder.convertAndSend()
 *
 * Python 벤치마크(bench.py)와의 차이:
 *   - Python: 브로커 raw 성능 (Spring 오버헤드 없음)
 *   - 이 테스트: 실제 Spring 서비스 코드를 거친 end-to-end latency
 *
 * 실행 방법:
 *   1) cd K6_TEST/broker-compare && docker-compose up -d
 *   2) ./gradlew test --tests '*.BrokerBenchmarkIntegrationTest' -DincludeTags=broker-bench
 *      (또는 IDE에서 @Disabled 제거 후 직접 실행)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RedisConfig.class,
        RedisPubSubService.class,
        KafkaConfig.class,
        ChatKafkaPublisher.class,
        ChatKafkaConsumer.class,
        BrokerBenchmarkIntegrationTest.BenchmarkConfig.class
})
@ActiveProfiles("kafka")
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.data.redis.password=",
        "spring.kafka.bootstrap-servers=localhost:9092"
})
@Disabled("로컬 Docker 환경에서만 수동 실행 — @Disabled 제거 또는 IDE에서 직접 실행\n" +
        "사전 조건: cd K6_TEST/broker-compare && docker-compose up -d")
class BrokerBenchmarkIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BrokerBenchmarkIntegrationTest.class);

    static final int    WARMUP  = 500;
    static final int    COUNT   = 5_000;
    static final long   ROOM_ID = 1L;
    static final String USER    = "bench@catxi.com";

    @Autowired @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    @Autowired ChatKafkaPublisher            kafkaPublisher;
    @Autowired LatencyRecorder               recorder;
    @Autowired ObjectMapper                  objectMapper;
    @Autowired RedisMessageListenerContainer redisListenerContainer;
    @Autowired KafkaListenerEndpointRegistry kafkaRegistry;

    // ────────────────────────────── Test ──────────────────────────────────────

    @Test
    void compareBrokers() throws Exception {
        log.info("=== Warmup {} msgs ===", WARMUP);
        runRedisBenchmark(WARMUP);
        runKafkaBenchmark(WARMUP);
        Thread.sleep(1_000);

        log.info("=== Benchmark {} msgs ===", COUNT);
        BenchResult redis = runRedisBenchmark(COUNT);
        Thread.sleep(1_000);
        BenchResult kafka = runKafkaBenchmark(COUNT);

        printResults(redis, kafka);
    }

    // ────────────────────────── Durability Test ───────────────────────────────

    /**
     * 컨슈머 재시작 내구성 테스트.
     *
     * 시나리오 (전송 700건):
     *   Phase 1 (200건): 컨슈머 정상 동작 → 정상 수신
     *   Phase 2 (300건): 컨슈머 강제 중단 → Redis는 유실, Kafka는 토픽에 보관
     *   Phase 3 (200건): 컨슈머 재시작   → Redis는 새 메시지만, Kafka는 보관 + 새 메시지
     *
     * 예상:
     *   Redis p99 유실률 ≈ 42.9% (300/700)
     *   Kafka p99 유실률 ≈ 0%
     */
    @Test
    void durabilityComparison() throws Exception {
        final int BEFORE  = 200;
        final int DURING  = 300;
        final int AFTER   = 200;
        final int TOTAL   = BEFORE + DURING + AFTER;

        log.info("=== 내구성 테스트: {}건 (다운 전 {} / 다운 중 {} / 재시작 후 {})", TOTAL, BEFORE, DURING, AFTER);

        int redisReceived = runRedisDurabilityTest(BEFORE, DURING, AFTER);
        Thread.sleep(500);
        int kafkaReceived = runKafkaDurabilityTest(BEFORE, DURING, AFTER);

        String sep = "=".repeat(72);
        System.out.println("\n" + sep);
        System.out.println("  내구성 테스트: 컨슈머 재시작 시 메시지 유실 비교");
        System.out.printf("  시나리오: 전송 %d건 (다운 전 %d / 다운 중 %d / 재시작 후 %d)%n", TOTAL, BEFORE, DURING, AFTER);
        System.out.println(sep);
        System.out.printf("  %-20s %8s / %d건   %6s건   %7s%n", "Broker", "수신", TOTAL, "유실", "유실률");
        System.out.println("-".repeat(72));
        System.out.printf("  %-20s %8d / %d건   %6d건   %6.1f%%%n",
                "Redis Pub/Sub", redisReceived, TOTAL, TOTAL - redisReceived, (TOTAL - redisReceived) * 100.0 / TOTAL);
        System.out.printf("  %-20s %8d / %d건   %6d건   %6.1f%%%n",
                "Kafka (KRaft)", kafkaReceived, TOTAL, TOTAL - kafkaReceived, (TOTAL - kafkaReceived) * 100.0 / TOTAL);
        System.out.println(sep);
        System.out.println("  [해석]");
        System.out.println("  Redis: fire-and-forget — 구독자 없는 동안 메시지 소멸");
        System.out.println("  Kafka: 오프셋 기반 — 재시작 후 미처리 메시지 전부 소비");
        System.out.println("  [채택 기준] 채팅은 수초 전 메시지만 의미있음 → 내구성 불필요 → Redis 유지");
        System.out.println(sep + "\n");
    }

    private int runRedisDurabilityTest(int before, int during, int after) throws Exception {
        int    total   = before + during + after;
        String channel = "redis-dur";
        recorder.prepare(channel, total);

        // Phase 1: 컨슈머 실행 중
        sendRedis(channel, 0, before);
        Thread.sleep(500);
        log.info("[Redis Dur] Phase1 sent={} received={}", before, recorder.count(channel));

        // Phase 2: 컨슈머 강제 중단
        redisListenerContainer.stop();
        Thread.sleep(200);
        sendRedis(channel, before, before + during);   // 아무도 수신 안 함 → 유실
        Thread.sleep(300);
        log.info("[Redis Dur] Phase2 sent={} (consumer down — messages lost)", during);

        // Phase 3: 컨슈머 재시작
        redisListenerContainer.start();
        Thread.sleep(300);
        sendRedis(channel, before + during, total);
        Thread.sleep(500);
        log.info("[Redis Dur] Phase3 sent={} total_received={}", after, recorder.count(channel));

        return recorder.count(channel);
    }

    private int runKafkaDurabilityTest(int before, int during, int after) throws Exception {
        int    total   = before + during + after;
        String channel = "kafka-dur";
        recorder.prepare(channel, total);

        // Phase 1: 컨슈머 실행 중
        sendKafka(channel, 0, before);
        Thread.sleep(2_000);   // Kafka polling 대기
        log.info("[Kafka Dur] Phase1 sent={} received={}", before, recorder.count(channel));

        // Phase 2: 컨슈머 중단 (Spring Kafka는 stop 시 오프셋 자동 커밋)
        kafkaRegistry.stop();
        Thread.sleep(500);
        sendKafka(channel, before, before + during);   // 토픽에 저장됨
        Thread.sleep(500);
        log.info("[Kafka Dur] Phase2 sent={} (stored in topic — consumer down)", during);

        // Phase 3: 컨슈머 재시작 → 저장된 메시지부터 catch-up
        kafkaRegistry.start();
        Thread.sleep(3_000);   // catch-up 소비 대기
        sendKafka(channel, before + during, total);

        boolean done = recorder.awaitCompletion(channel, 30);
        log.info("[Kafka Dur] Phase3 sent={} total_received={} done={}", after, recorder.count(channel), done);

        return recorder.count(channel);
    }

    private void sendRedis(String channel, int from, int to) throws Exception {
        for (int i = from; i < to; i++) {
            String msgId = "dur-" + i;
            recorder.recordSend(channel, msgId, System.nanoTime());
            ChatMessageSendReq req = new ChatMessageSendReq(ROOM_ID, USER, msgId, LocalDateTime.now());
            redisTemplate.convertAndSend("chat", objectMapper.writeValueAsString(req));
        }
    }

    private void sendKafka(String channel, int from, int to) {
        for (int i = from; i < to; i++) {
            String msgId = "dur-" + i;
            recorder.recordSend(channel, msgId, System.nanoTime());
            ChatMessageSendReq req = new ChatMessageSendReq(ROOM_ID, USER, msgId, LocalDateTime.now());
            kafkaPublisher.publish(req);
        }
    }

    // ────────────────────────── Benchmark runners ─────────────────────────────

    private BenchResult runRedisBenchmark(int count) throws Exception {
        recorder.prepare("redis", count);
        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            String msgId  = UUID.randomUUID().toString();
            long   sendNs = System.nanoTime();
            recorder.recordSend("redis", msgId, sendNs);

            ChatMessageSendReq req = new ChatMessageSendReq(ROOM_ID, USER, msgId, LocalDateTime.now());
            redisTemplate.convertAndSend("chat", objectMapper.writeValueAsString(req));
        }

        boolean done    = recorder.awaitCompletion("redis", 30);
        long    elapsed = System.nanoTime() - start;
        log.info("[Redis] received={}/{} done={}", recorder.count("redis"), count, done);
        return BenchResult.of("Redis Pub/Sub", recorder.latencies("redis"), elapsed, count);
    }

    private BenchResult runKafkaBenchmark(int count) throws Exception {
        recorder.prepare("kafka", count);
        long start = System.nanoTime();

        for (int i = 0; i < count; i++) {
            String msgId  = UUID.randomUUID().toString();
            long   sendNs = System.nanoTime();
            recorder.recordSend("kafka", msgId, sendNs);

            ChatMessageSendReq req = new ChatMessageSendReq(ROOM_ID, USER, msgId, LocalDateTime.now());
            kafkaPublisher.publish(req);
        }

        boolean done    = recorder.awaitCompletion("kafka", 60);
        long    elapsed = System.nanoTime() - start;
        log.info("[Kafka] received={}/{} done={}", recorder.count("kafka"), count, done);
        return BenchResult.of("Kafka (KRaft)", recorder.latencies("kafka"), elapsed, count);
    }

    // ────────────────────────────── Print ─────────────────────────────────────

    private void printResults(BenchResult redis, BenchResult kafka) {
        String sep = "=".repeat(72);
        System.out.println("\n" + sep);
        System.out.println("  Spring Service-Level Broker Benchmark");
        System.out.println("  경로: Publisher → Broker → Consumer → SimpMessageSendingOperations");
        System.out.println(sep);
        System.out.printf("  %-20s %10s %10s %10s %12s%n", "Broker", "p50(ms)", "p95(ms)", "p99(ms)", "msg/s");
        System.out.println("-".repeat(72));
        System.out.println(redis);
        System.out.println(kafka);
        System.out.println(sep);
        System.out.printf("  [결론] Redis p99=%.2fms  vs  Kafka p99=%.2fms%n", redis.p99(), kafka.p99());
        System.out.println("  " + (redis.p99() <= kafka.p99()
                ? "→ Redis Pub/Sub 지연 우위 + 운영 단순성 → Kafka 도입 불필요"
                : "→ Kafka 지연 우위 — 내구성 요구 시 재검토"));
        System.out.println(sep + "\n");
    }

    // ────────────────────────── Result record ─────────────────────────────────

    record BenchResult(String label, double p50, double p95, double p99, double throughput) {
        static BenchResult of(String label, List<Long> nsL, long elapsedNs, int count) {
            if (nsL.isEmpty()) return new BenchResult(label, -1, -1, -1, 0);
            List<Long> s = nsL.stream().sorted().toList();
            double ms = 1_000_000.0;
            return new BenchResult(label,
                    s.get(Math.max(0, (int)(s.size() * 0.50) - 1)) / ms,
                    s.get(Math.max(0, (int)(s.size() * 0.95) - 1)) / ms,
                    s.get(Math.max(0, (int)(s.size() * 0.99) - 1)) / ms,
                    count / (elapsedNs / 1_000_000_000.0));
        }
        @Override public String toString() {
            return String.format("  %-20s %10.2f %10.2f %10.2f %12.0f", label, p50, p95, p99, throughput);
        }
    }

    // ────────────────── LatencyRecorder (SimpMessageSendingOperations) ─────────

    /**
     * SimpMessageSendingOperations 자리에 주입되는 레이턴시 기록기.
     * convertAndSend(destination, payload) 호출 시 send_ns → receive_ns 차이를 기록.
     * payload.message() 필드에 UUID를 embed해 어느 메시지인지 식별.
     */
    static class LatencyRecorder implements SimpMessageSendingOperations {
        private final Map<String, ConcurrentHashMap<String, Long>> sendTimes = new ConcurrentHashMap<>();
        private final Map<String, List<Long>>                      latencies = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch>                  latches   = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger>                   counts    = new ConcurrentHashMap<>();

        void prepare(String ch, int n) {
            sendTimes.put(ch, new ConcurrentHashMap<>());
            latencies.put(ch, Collections.synchronizedList(new ArrayList<>()));
            latches.put(ch, new CountDownLatch(n));
            counts.put(ch, new AtomicInteger(0));
        }

        void recordSend(String ch, String msgId, long sendNs) {
            sendTimes.get(ch).put(msgId, sendNs);
        }

        List<Long> latencies(String ch) { return latencies.getOrDefault(ch, Collections.emptyList()); }
        int        count(String ch)     { return counts.getOrDefault(ch, new AtomicInteger()).get(); }

        boolean awaitCompletion(String ch, int timeoutSec) throws InterruptedException {
            CountDownLatch latch = latches.get(ch);
            return latch != null && latch.await(timeoutSec, TimeUnit.SECONDS);
        }

        // ── 실제 측정 포인트 ─────────────────────────────────────────────────
        @Override
        public void convertAndSend(String destination, Object payload) {
            if (!(payload instanceof ChatMessageSendReq msg)) return;
            long   receiveNs = System.nanoTime();
            String msgId     = msg.message();   // UUID가 message 필드에 embed됨

            for (String ch : sendTimes.keySet()) {   // 동적 조회 — durability 채널 포함
                ConcurrentHashMap<String, Long> times = sendTimes.get(ch);
                if (times == null) continue;
                Long sendNs = times.remove(msgId);
                if (sendNs != null) {
                    latencies.get(ch).add(receiveNs - sendNs);
                    counts.get(ch).incrementAndGet();
                    latches.get(ch).countDown();
                    return;
                }
            }
        }

        // ── No-op implementations (벤치마크에서 사용하지 않는 메서드) ─────────
        @Override public void send(Message<?> msg) {}
        @Override public void send(String dest, Message<?> msg) {}
        @Override public void convertAndSend(Object payload) {}
        @Override public void convertAndSend(Object payload, MessagePostProcessor pp) {}
        @Override public void convertAndSend(String dest, Object payload, Map<String, Object> headers) {}
        @Override public void convertAndSend(String dest, Object payload, MessagePostProcessor pp) {}
        @Override public void convertAndSend(String dest, Object payload, Map<String, Object> headers, MessagePostProcessor pp) {}
        @Override public void convertAndSendToUser(String user, String dest, Object payload) {}
        @Override public void convertAndSendToUser(String user, String dest, Object payload, Map<String, Object> headers) {}
        @Override public void convertAndSendToUser(String user, String dest, Object payload, MessagePostProcessor pp) {}
        @Override public void convertAndSendToUser(String user, String dest, Object payload, Map<String, Object> headers, MessagePostProcessor pp) {}
    }

    // ────────────────────────── TestConfiguration ─────────────────────────────

    @TestConfiguration
    static class BenchmarkConfig {
        @Bean
        public LatencyRecorder latencyRecorder() {
            return new LatencyRecorder();
        }

        /** RedisPubSubService·ChatKafkaConsumer가 autowire하는 SimpMessageSendingOperations */
        @Bean @Primary
        public SimpMessageSendingOperations simpMessagingTemplate(LatencyRecorder recorder) {
            return recorder;
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }
}
