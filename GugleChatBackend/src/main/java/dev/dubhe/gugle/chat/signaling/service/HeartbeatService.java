package dev.dubhe.gugle.chat.signaling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private final Map<String, Long> lastResponse = new ConcurrentHashMap<>();
    private final Map<String, Integer> missedCount = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionUserIds = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    public HeartbeatService(SimpMessagingTemplate messagingTemplate, RoomService roomService) {
        this.messagingTemplate = messagingTemplate;
        this.roomService = roomService;
    }

    public void registerSession(String sessionId, Long userId) {
        sessionUserIds.put(sessionId, userId);
        lastResponse.put(sessionId, System.currentTimeMillis());
    }

    public void onHeartbeatResponse(String simpSessionId) {
        lastResponse.put(simpSessionId, System.currentTimeMillis());
        missedCount.remove(simpSessionId);
    }

    public void onSessionDisconnected(String simpSessionId) {
        Long userId = sessionUserIds.remove(simpSessionId);
        lastResponse.remove(simpSessionId);
        missedCount.remove(simpSessionId);
        if (userId != null) {
            roomService.leaveRoom(userId);
            log.info("Session {} (user {}) disconnected — removed from voice", simpSessionId, userId);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void sendHeartbeat() {
        long now = System.currentTimeMillis();
        messagingTemplate.convertAndSend("/topic/heartbeat",
                Map.of("type", "ping", "ts", now));

        for (var entry : lastResponse.entrySet()) {
            if (now - entry.getValue() > 30000) {
                int missed = missedCount.merge(entry.getKey(), 1, Integer::sum);
                if (missed >= 3) {
                    Long userId = sessionUserIds.get(entry.getKey());
                    log.warn("Session {} (user {}) timed out — 3 missed heartbeats", entry.getKey(), userId);
                    if (userId != null) roomService.leaveRoom(userId);
                    lastResponse.remove(entry.getKey());
                    missedCount.remove(entry.getKey());
                    sessionUserIds.remove(entry.getKey());
                }
            }
        }
    }
}
