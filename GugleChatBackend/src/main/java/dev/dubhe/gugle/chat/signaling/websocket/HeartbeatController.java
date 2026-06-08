package dev.dubhe.gugle.chat.signaling.websocket;

import dev.dubhe.gugle.chat.signaling.service.HeartbeatService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Controller
public class HeartbeatController {

    private final HeartbeatService heartbeatService;

    public HeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor accessor) {
        heartbeatService.onHeartbeatResponse(accessor.getSessionId());
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
