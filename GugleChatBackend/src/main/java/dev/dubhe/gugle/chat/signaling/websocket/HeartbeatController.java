package dev.dubhe.gugle.chat.signaling.websocket;

import dev.dubhe.gugle.chat.signaling.service.HeartbeatService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@Controller
public class HeartbeatController {

    private final HeartbeatService heartbeatService;
    private final SimpMessagingTemplate messagingTemplate;

    public HeartbeatController(HeartbeatService heartbeatService, SimpMessagingTemplate messagingTemplate) {
        this.heartbeatService = heartbeatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor accessor, @Payload(required = false) Map<String, Object> body) {
        heartbeatService.onHeartbeatResponse(accessor.getSessionId());
        // Echo back pingTs for client-side RTT measurement
        if (body != null && body.containsKey("pingTs")) {
            Principal user = accessor.getUser();
            if (user != null) {
                messagingTemplate.convertAndSendToUser(
                    user.getName(),
                    "/queue/heartbeat",
                    Map.of("type", "pong", "pingTs", body.get("pingTs"))
                );
            }
        }
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        var headers = event.getMessage().getHeaders();
        String sessionId = headers.get("simpSessionId").toString();
        Object principal = headers.get("simpUser");
        long userId = 0L;
        if (principal instanceof java.security.Principal p) {
            try { userId = Long.parseLong(p.getName()); } catch (NumberFormatException ignored) {}
        }
        heartbeatService.registerSession(sessionId, userId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        heartbeatService.onSessionDisconnected(event.getSessionId());
    }
}
