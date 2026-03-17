package com.project.catxi.fcm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * FCM 배치 크기 동적 최적화 서비스
 * 트래픽 패턴과 성능 메트릭을 기반으로 배치 크기를 자동 조정
 */
@Slf4j
@Component
public class FcmBatchOptimizer {

    // 기본 설정
    private static final int MIN_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 500;
    private static final int DEFAULT_BATCH_SIZE = 200;

    // 동적 배치 크기
    private final AtomicInteger currentBatchSize = new AtomicInteger(DEFAULT_BATCH_SIZE);

    // 성능 메트릭
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder totalSuccesses = new LongAdder();
    private final LongAdder totalFailures = new LongAdder();
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicLong lastOptimizationTime = new AtomicLong(System.currentTimeMillis());

    // 최적화 설정
    private static final long OPTIMIZATION_INTERVAL_MS = 60000; // 1분마다 최적화
    private static final double TARGET_SUCCESS_RATE = 0.95; // 목표 성공률 95%
    private static final long TARGET_LATENCY_MS = 2000; // 목표 지연시간 2초

    /**
     * 현재 최적화된 배치 크기 반환
     */
    public int getOptimalBatchSize() {
        optimizeIfNeeded();
        return currentBatchSize.get();
    }

    /**
     * 배치 처리 결과 기록
     */
    public void recordBatchResult(int batchSize, int successCount, int failureCount, long latencyMs) {
        totalRequests.increment();
        totalSuccesses.add(successCount);
        totalFailures.add(failureCount);
        totalLatency.addAndGet(latencyMs);

        log.debug("배치 결과 기록 - Size: {}, Success: {}, Failure: {}, Latency: {}ms",
                batchSize, successCount, failureCount, latencyMs);
    }

    /**
     * 필요시 배치 크기 최적화 수행
     */
    private void optimizeIfNeeded() {
        long currentTime = System.currentTimeMillis();
        long lastOptimization = lastOptimizationTime.get();

        if (currentTime - lastOptimization >= OPTIMIZATION_INTERVAL_MS) {
            if (lastOptimizationTime.compareAndSet(lastOptimization, currentTime)) {
                optimizeBatchSize();
            }
        }
    }

    /**
     * 트래픽 패턴과 성능 메트릭을 기반으로 배치 크기 최적화
     */
    private void optimizeBatchSize() {
        try {
            long requests = totalRequests.sum();
            if (requests < 10) {
                // 데이터가 충분하지 않은 경우 기본값 유지
                log.debug("배치 최적화 스킵 - 데이터 부족 (요청 수: {})", requests);
                return;
            }

            double successRate = (double) totalSuccesses.sum() / (totalSuccesses.sum() + totalFailures.sum());
            double avgLatency = requests > 0 ? (double) totalLatency.get() / requests : 0;

            int currentSize = currentBatchSize.get();
            int newSize = calculateOptimalSize(currentSize, successRate, avgLatency);

            if (newSize != currentSize) {
                currentBatchSize.set(newSize);
                log.info("배치 크기 최적화 - 기존: {}, 신규: {}, 성공률: {:.2%}, 평균지연: {:.0f}ms",
                        currentSize, newSize, successRate, avgLatency);
            }

            // 메트릭 리셋 (최근 성능만 반영하기 위해)
            resetMetrics();

        } catch (Exception e) {
            log.error("배치 크기 최적화 중 오류 발생", e);
        }
    }

    /**
     * 성능 지표를 기반으로 최적 배치 크기 계산
     */
    private int calculateOptimalSize(int currentSize, double successRate, double avgLatency) {
        int newSize = currentSize;

        // 1. 시간대별 트래픽 패턴 고려
        int timeBasedSize = getTimeBasedBatchSize();

        // 2. 성공률 기반 조정
        if (successRate < TARGET_SUCCESS_RATE) {
            // 성공률이 낮으면 배치 크기 감소
            newSize = Math.max(currentSize - 50, MIN_BATCH_SIZE);
            log.debug("성공률 낮음 ({:.2%}) - 배치 크기 감소: {} -> {}", successRate, currentSize, newSize);
        } else if (successRate > 0.98 && avgLatency < TARGET_LATENCY_MS) {
            // 성공률이 매우 높고 지연시간이 목표 이하면 배치 크기 증가
            newSize = Math.min(currentSize + 25, MAX_BATCH_SIZE);
            log.debug("고성능 상태 - 배치 크기 증가: {} -> {}", currentSize, newSize);
        }

        // 3. 지연시간 기반 조정
        if (avgLatency > TARGET_LATENCY_MS * 1.5) {
            // 지연시간이 목표의 1.5배를 초과하면 크기 감소
            newSize = Math.max(newSize - 75, MIN_BATCH_SIZE);
            log.debug("지연시간 높음 ({:.0f}ms) - 배치 크기 감소: {} -> {}", avgLatency, currentSize, newSize);
        }

        // 4. 시간대별 권장 크기와 조정
        newSize = adjustForTimePattern(newSize, timeBasedSize);

        return Math.max(MIN_BATCH_SIZE, Math.min(newSize, MAX_BATCH_SIZE));
    }

    /**
     * 시간대별 권장 배치 크기 반환
     */
    private int getTimeBasedBatchSize() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();

        // 시간대별 트래픽 패턴
        if (hour >= 7 && hour <= 9) {
            // 아침 출근시간 - 높은 트래픽
            return 400;
        } else if (hour >= 12 && hour <= 14) {
            // 점심시간 - 중간 트래픽
            return 300;
        } else if (hour >= 18 && hour <= 22) {
            // 저녁시간 - 높은 트래픽
            return 450;
        } else if (hour >= 23 || hour <= 6) {
            // 새벽시간 - 낮은 트래픽
            return 100;
        } else {
            // 일반 시간 - 기본 트래픽
            return DEFAULT_BATCH_SIZE;
        }
    }

    /**
     * 시간대별 패턴과 현재 성능을 조합하여 최종 크기 조정
     */
    private int adjustForTimePattern(int performanceBasedSize, int timeBasedSize) {
        // 가중 평균 (성능 기반 70%, 시간대 기반 30%)
        int adjustedSize = (int) (performanceBasedSize * 0.7 + timeBasedSize * 0.3);

        // 급격한 변화 방지 (현재 크기의 ±50% 이내로 제한)
        int currentSize = currentBatchSize.get();
        int maxIncrease = currentSize + (currentSize / 2);
        int maxDecrease = currentSize - (currentSize / 2);

        adjustedSize = Math.max(maxDecrease, Math.min(adjustedSize, maxIncrease));

        log.debug("배치 크기 조정 - 성능기반: {}, 시간기반: {}, 최종: {}",
                performanceBasedSize, timeBasedSize, adjustedSize);

        return adjustedSize;
    }

    /**
     * 메트릭 초기화
     */
    private void resetMetrics() {
        totalRequests.reset();
        totalSuccesses.reset();
        totalFailures.reset();
        totalLatency.set(0);
    }

    /**
     * 현재 성능 통계 반환
     */
    public BatchPerformanceStats getCurrentStats() {
        long requests = totalRequests.sum();
        long successes = totalSuccesses.sum();
        long failures = totalFailures.sum();
        double successRate = (successes + failures) > 0 ? (double) successes / (successes + failures) : 0;
        double avgLatency = requests > 0 ? (double) totalLatency.get() / requests : 0;

        return new BatchPerformanceStats(
                currentBatchSize.get(),
                requests,
                successRate,
                avgLatency,
                getTimeBasedBatchSize()
        );
    }

    /**
     * 배치 성능 통계 클래스
     */
    public record BatchPerformanceStats(
            int currentBatchSize,
            long totalRequests,
            double successRate,
            double avgLatencyMs,
            int recommendedTimeBasedSize
    ) {}

    /**
     * 최적화 상태 정보 반환
     */
    public String getOptimizationInfo() {
        BatchPerformanceStats stats = getCurrentStats();
        return String.format(
                "배치최적화 [현재크기: %d, 요청수: %d, 성공률: %.1f%%, 평균지연: %.0fms, 시간권장: %d]",
                stats.currentBatchSize(),
                stats.totalRequests(),
                stats.successRate() * 100,
                stats.avgLatencyMs(),
                stats.recommendedTimeBasedSize()
        );
    }
}