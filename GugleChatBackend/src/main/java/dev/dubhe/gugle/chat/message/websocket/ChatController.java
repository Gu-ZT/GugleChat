package dev.dubhe.gugle.chat.message.websocket;

import dev.dubhe.gugle.chat.message.dto.MessageResponse;
import dev.dubhe.gugle.chat.message.dto.SendMessageRequest;
import dev.dubhe.gugle.chat.message.service.MessageService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
public class ChatController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send/{channelId}")
    public void sendMessage(@DestinationVariable Long channelId,
                            @Payload SendMessageRequest request, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        MessageResponse response = messageService.sendMessage(channelId, userId, request);
        messagingTemplate.convertAndSend("/topic/channel." + channelId, response);
    }

    @MessageMapping("/chat.edit/{messageId}")
    public void editMessage(@DestinationVariable Long messageId,
                            @Payload SendMessageRequest request, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        MessageResponse response = messageService.editMessage(messageId, userId, request.getContent());
        messagingTemplate.convertAndSend("/topic/channel." + response.getChannelId(), response);
    }

    @MessageMapping("/chat.delete/{messageId}")
    public void deleteMessage(@DestinationVariable Long messageId, Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        Long channelId = messageService.getChannelId(messageId);
        messageService.deleteMessage(messageId, userId);
        messagingTemplate.convertAndSend("/topic/channel." + channelId,
                Map.of("type", "DELETE", "messageId", messageId));
    }
}
