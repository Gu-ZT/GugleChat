package dev.dubhe.gugle.chat.signaling.websocket;

import dev.dubhe.gugle.chat.signaling.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

@Controller
public class SignalingHandler {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    public SignalingHandler(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/rtc.join/{roomId}")
    public void joinRoom(@DestinationVariable Long roomId, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        Set<Long> others = roomService.joinRoom(roomId, userId);

        // Notify existing members about the new user
        for (Long otherId : others) {
            messagingTemplate.convertAndSendToUser(
                    otherId.toString(), "/queue/rtc",
                    Map.of("type", "user-joined", "userId", userId));
        }

        // Send the new user the list of existing members
        messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/rtc",
                Map.of("type", "room-users", "users", others));
    }

    @MessageMapping("/rtc.leave/{roomId}")
    public void leaveRoom(@DestinationVariable Long roomId, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        Set<Long> remaining = roomService.leaveRoom(userId);

        for (Long otherId : remaining) {
            messagingTemplate.convertAndSendToUser(
                    otherId.toString(), "/queue/rtc",
                    Map.of("type", "user-left", "userId", userId));
        }
    }

    @MessageMapping("/rtc.offer")
    public void handleOffer(@Payload Map<String, Object> payload, Principal principal) {
        Long targetId = Long.valueOf(payload.get("target").toString());
        Long senderId = Long.parseLong(principal.getName());
        messagingTemplate.convertAndSendToUser(targetId.toString(), "/queue/rtc",
                Map.of("type", "offer", "sdp", payload.get("sdp"), "userId", senderId));
    }

    @MessageMapping("/rtc.answer")
    public void handleAnswer(@Payload Map<String, Object> payload, Principal principal) {
        Long targetId = Long.valueOf(payload.get("target").toString());
        Long senderId = Long.parseLong(principal.getName());
        messagingTemplate.convertAndSendToUser(targetId.toString(), "/queue/rtc",
                Map.of("type", "answer", "sdp", payload.get("sdp"), "userId", senderId));
    }

    @MessageMapping("/rtc.ice-candidate")
    public void handleIceCandidate(@Payload Map<String, Object> payload, Principal principal) {
        Long targetId = Long.valueOf(payload.get("target").toString());
        Long senderId = Long.parseLong(principal.getName());
        messagingTemplate.convertAndSendToUser(targetId.toString(), "/queue/rtc",
                Map.of("type", "ice-candidate", "candidate", payload.get("candidate"), "userId", senderId));
    }
}
