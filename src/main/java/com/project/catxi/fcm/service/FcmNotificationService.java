package com.project.catxi.fcm.service;

import com.google.firebase.messaging.*;
import com.project.catxi.member.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmNotificationService {

    private final FirebaseMessaging firebaseMessaging;
    private final FcmTokenService fcmTokenService;
    private final FcmBatchOptimizer batchOptimizer;

    private boolean isFirebaseInitialized() {
        return firebaseMessaging != null;
    }

    /**
     * 채팅 메시지 알림 발송 (동기 - Redis Consumer에서 호출용)
     * @param targetMember 알림을 받을 사용자
     * @param senderNickname 메시지 발송자 닉네임
     * @param message 채팅 내용
     */
    public void sendChatNotificationSync(Member targetMember, String senderNickname, String message) {
        try {
            if (!isFirebaseInitialized()) {
                log.warn("Firebase가 초기화되지 않아 채팅 알림을 발송할 수 없습니다.");
                return;
            }

            List<String> tokens = fcmTokenService.getActiveTokens(targetMember);
            if (!tokens.isEmpty()) {
                String title = "새로운 채팅 메시지";
                String body = String.format("%s: %s", senderNickname, message);
                sendMulticastNotification(tokens, title, body, "CHAT");
                log.debug("채팅 알림 발송 완료 - Member ID: {}", targetMember.getId());
            }

        } catch (Exception e) {
            log.error("채팅 알림 발송 실패 - Member ID: {}", targetMember.getId(), e);
        }
    }

    /**
     * 방장의 준비요청 알림 발송 (성능 최적화 - 배치 처리)
     * @param targetMembers 알림을 받을 사용자들
     * @param roomId 채팅방 ID
     */
    public void sendReadyRequestNotificationSync(List<Member> targetMembers, Long roomId) {
        try {
            if (!isFirebaseInitialized()) {
                log.warn("Firebase가 초기화되지 않아 준비요청 알림을 발송할 수 없습니다.");
                return;
            }

            // 모든 토큰을 한 번에 수집하여 배치 처리
            List<String> allTokens = targetMembers.stream()
                    .flatMap(member -> fcmTokenService.getActiveTokens(member).stream())
                    .distinct() // 중복 토큰 제거
                    .toList();

            if (!allTokens.isEmpty()) {
                String title = "준비 요청";
                String body = "방장이 준비요청을 보냈습니다";
                sendMulticastNotification(allTokens, title, body, "READY_REQUEST", roomId);

                log.debug("준비요청 알림 발송 완료 - Room ID: {}, Targets: {}, Tokens: {}",
                         roomId, targetMembers.size(), allTokens.size());
            } else {
                log.debug("준비요청 알림 대상 토큰 없음 - Room ID: {}", roomId);
            }

        } catch (Exception e) {
            log.error("준비요청 알림 발송 실패 - Room ID: {}", roomId, e);
        }
    }

    /**
     * 멀티캐스트 알림 발송
     */
    private void sendMulticastNotification(List<String> tokens, String title, String body, String type) {
        sendMulticastNotification(tokens, title, body, type, null);
    }

    private void sendMulticastNotification(List<String> tokens, String title, String body, String type, Long roomId) {
        try {
            if (!isFirebaseInitialized()) {
                log.warn("FirebaseMessaging이 초기화되지 않아 알림을 발송할 수 없습니다.");
                return;
            }

            // 토큰 유효성 검사
            List<String> validTokens = tokens.stream()
                    .filter(this::isValidFcmToken)
                    .toList();

            if (validTokens.isEmpty()) {
                log.debug("유효한 FCM 토큰이 없습니다.");
                return;
            }

            // 동적 배치 크기 조정
            final int BATCH_SIZE = batchOptimizer.getOptimalBatchSize();
            AtomicInteger totalSuccessCount = new AtomicInteger(0);
            AtomicInteger totalFailureCount = new AtomicInteger(0);

            for (int i = 0; i < validTokens.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, validTokens.size());
                List<String> batchTokens = validTokens.subList(i, end);

                sendMulticastBatch(batchTokens, title, body, type, roomId, totalSuccessCount, totalFailureCount);
            }

            log.info("FCM 알림 발송 완료 - 성공: {}, 실패: {}, 타입: {}, 배치크기: {}, 최적화정보: [{}]",
                    totalSuccessCount.get(), totalFailureCount.get(), type, BATCH_SIZE, batchOptimizer.getOptimizationInfo());

        } catch (Exception e) {
            log.error("FCM 알림 발송 중 예외 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 멀티캐스트 배치 전송 (성능 최적화 + 동적 배치 크기)
     */
    private void sendMulticastBatch(List<String> tokens, String title, String body, String type, Long roomId,
                                    AtomicInteger totalSuccessCount, AtomicInteger totalFailureCount) {
        long startTime = System.currentTimeMillis();
        try {
            // 멀티캐스트 메시지 생성
            MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .putData("type", type)
                    .putData("title", title)
                    .putData("body", body);

            if (roomId != null) {
                messageBuilder.putData("roomId", roomId.toString());
            }

            MulticastMessage message = messageBuilder.build();

            // 배치 전송 (한 번의 API 호출로 최대 500개 토큰 처리)
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);

            int successCount = response.getSuccessCount();
            int failureCount = response.getFailureCount();

            totalSuccessCount.addAndGet(successCount);
            totalFailureCount.addAndGet(failureCount);

            // 배치 성능 메트릭 기록
            long latency = System.currentTimeMillis() - startTime;
            batchOptimizer.recordBatchResult(tokens.size(), successCount, failureCount, latency);

            log.debug("FCM 배치 전송 완료 - 성공: {}, 실패: {}, 배치크기: {}, 지연: {}ms",
                    successCount, failureCount, tokens.size(), latency);

            // 실패한 토큰들 처리
            if (failureCount > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse sendResponse = responses.get(i);
                    if (!sendResponse.isSuccessful()) {
                        String token = tokens.get(i);
                        FirebaseMessagingException exception = sendResponse.getException();

                        if (exception != null &&
                                (exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                                        exception.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                            log.info("유효하지 않은 FCM 토큰 제거: {}",
                                    token.substring(0, Math.min(20, token.length())) + "...");
                            fcmTokenService.removeInvalidFcmToken(token);
                        } else {
                            log.warn("FCM 토큰 전송 실패 - Token: {}, Error: {}",
                                    token.substring(0, Math.min(20, token.length())) + "...",
                                    exception != null ? exception.getMessage() : "Unknown error");
                        }
                    }
                }
            }

        } catch (FirebaseMessagingException e) {
            totalFailureCount.addAndGet(tokens.size());

            // 실패 메트릭 기록
            long latency = System.currentTimeMillis() - startTime;
            batchOptimizer.recordBatchResult(tokens.size(), 0, tokens.size(), latency);

            // 재시도 가능한 오류인지 확인
            if (isRetryableError(e)) {
                log.warn("FCM 배치 전송 재시도 가능한 오류 - 배치크기: {}, Error: {}",
                        tokens.size(), e.getMessage());
                // 재시도 로직 구현 (지수 백오프)
                retryWithBackoff(tokens, title, body, type, roomId, 1);
            } else {
                log.error("FCM 배치 전송 실패 - 배치크기: {}, Error: {}", tokens.size(), e.getMessage());
            }
        } catch (Exception e) {
            totalFailureCount.addAndGet(tokens.size());

            // 실패 메트릭 기록
            long latency = System.currentTimeMillis() - startTime;
            batchOptimizer.recordBatchResult(tokens.size(), 0, tokens.size(), latency);

            log.error("FCM 배치 전송 예상치 못한 오류 - 배치크기: {}, Error: {}",
                    tokens.size(), e.getMessage(), e);
        }
    }

    /**
     * 재시도 가능한 Firebase 오류인지 확인
     */
    private boolean isRetryableError(FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.INTERNAL ||
               errorCode == MessagingErrorCode.UNAVAILABLE ||
               (e.getHttpResponse() != null &&
                (e.getHttpResponse().getStatusCode() == 500 ||
                 e.getHttpResponse().getStatusCode() == 503));
    }

    /**
     * FCM 토큰 유효성 검사 (강화된 검증)
     */
    private boolean isValidFcmToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // FCM 토큰 최소 길이 검증 (일반적으로 152자 이상)
        if (token.length() < 140) {
            return false;
        }

        // FCM 토큰은 Base64 URL-safe 문자와 콜론(:) 포함
        return token.matches("^[A-Za-z0-9_:-]+$");
    }

    /**
     * 재시도 로직 (지수 백오프)
     */
    private void retryWithBackoff(List<String> tokens, String title, String body, String type, Long roomId, int retryCount) {
        final int MAX_RETRIES = 3;
        if (retryCount > MAX_RETRIES) {
            log.error("FCM 재시도 한계 초과 - 배치크기: {}, 최종 포기", tokens.size());
            return;
        }

        try {
            // 지수 백오프: 2^retryCount 초 대기
            long delayMs = (long) Math.pow(2, retryCount) * 1000;
            Thread.sleep(delayMs);

            log.info("FCM 재시도 시도 {}/{} - 배치크기: {}, 대기시간: {}ms",
                    retryCount, MAX_RETRIES, tokens.size(), delayMs);

            // 재시도 실행
            sendMulticastBatch(tokens, title, body, type, roomId,
                             new AtomicInteger(0), new AtomicInteger(0));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FCM 재시도 중 인터럽트 발생");
        } catch (Exception e) {
            if (e instanceof FirebaseMessagingException && isRetryableError((FirebaseMessagingException) e)) {
                log.warn("FCM 재시도 {}회차 실패, 다시 시도 - Error: {}", retryCount, e.getMessage());
                retryWithBackoff(tokens, title, body, type, roomId, retryCount + 1);
            } else {
                log.error("FCM 재시도 불가능한 오류 - 재시도 중단: {}", e.getMessage());
            }
        }
    }
}