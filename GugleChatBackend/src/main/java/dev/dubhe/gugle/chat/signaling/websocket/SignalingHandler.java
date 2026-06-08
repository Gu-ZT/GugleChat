package dev.dubhe.gugle.chat.signaling.websocket;

import dev.dubhe.gugle.chat.signaling.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;

@Controller
public class SignalingHandler {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public SignalingHandler(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/rtc.join/{roomId}")
    public void joinRoom(@DestinationVariable Long roomId, Principal principal,
                         SimpMessageHeaderAccessor accessor) {
        Long userId = Long.parseLong(principal.getName());
        Object uname = accessor.getSessionAttributes() != null
                ? accessor.getSessionAttributes().get("username") : null;
        String username = uname != null ? uname.toString() : "User" + userId;
        roomService.setUsername(userId, username);
        Set<Long> others = roomService.joinRoom(roomId, userId);

        for (Long otherId : others) {
            messagingTemplate.convertAndSendToUser(otherId.toString(), "/queue/rtc",
                    Map.of("type", "user-joined", "userId", userId, "username", username));
        }
        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/rtc",
                Map.of("type", "room-users", "users", others));

        broadcastVoiceUsers(roomId);
    }

    @MessageMapping("/rtc.leave/{roomId}")
    public void leaveRoom(@DestinationVariable Long roomId, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        Set<Long> remaining = roomService.leaveRoom(userId);

        for (Long otherId : remaining) {
            messagingTemplate.convertAndSendToUser(otherId.toString(), "/queue/rtc",
                    Map.of("type", "user-left", "userId", userId));
        }
        broadcastVoiceUsers(roomId);
    }

    private void broadcastVoiceUsers(Long roomId) {
        messagingTemplate.convertAndSend("/topic/channel." + roomId,
                Map.of("type", "voice-users", "users", roomService.getRoomUsers(roomId)));
    }

    @MessageMapping("/rtc.offer")
    public void handleOffer(@Payload Map<String, Object> payload, Principal principal) {
        messagingTemplate.convertAndSendToUser(
                payload.get("target").toString(), "/queue/rtc",
                Map.of("type", "offer", "sdp", payload.get("sdp"),
                       "userId", Long.parseLong(principal.getName()),
                       "username", payload.getOrDefault("username", "")));
    }

    @MessageMapping("/rtc.answer")
    public void handleAnswer(@Payload Map<String, Object> payload, Principal principal) {
        messagingTemplate.convertAndSendToUser(
                payload.get("target").toString(), "/queue/rtc",
                Map.of("type", "answer", "sdp", payload.get("sdp"),
                       "userId", Long.parseLong(principal.getName()),
                       "username", payload.getOrDefault("username", "")));
    }

    @MessageMapping("/rtc.ice-candidate")
    public void handleIceCandidate(@Payload Map<String, Object> payload, Principal principal) {
        messagingTemplate.convertAndSendToUser(
                payload.get("target").toString(), "/queue/rtc",
                Map.of("type", "ice-candidate", "candidate", payload.get("candidate"),
                       "userId", Long.parseLong(principal.getName()),
                       "username", payload.getOrDefault("username", "")));
    }
}
