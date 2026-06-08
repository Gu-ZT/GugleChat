package dev.dubhe.gugle.chat.message.controller;

import dev.dubhe.gugle.chat.common.dto.ApiResponse;
import dev.dubhe.gugle.chat.message.dto.MessageResponse;
import dev.dubhe.gugle.chat.message.service.MessageService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) { this.messageService = messageService; }

    @GetMapping("/channels/{channelId}/messages")
    public ApiResponse<List<MessageResponse>> getHistory(@PathVariable Long channelId,
                                                          @RequestParam(required = false) Long before,
                                                          Authentication auth) {
        return ApiResponse.ok(messageService.getHistory(channelId, (Long) auth.getPrincipal(), before));
    }

    @PutMapping("/messages/{messageId}")
    public ApiResponse<MessageResponse> edit(@PathVariable Long messageId,
                                              @RequestBody Map<String, String> body,
                                              Authentication auth) {
        return ApiResponse.ok(messageService.editMessage(messageId,
                (Long) auth.getPrincipal(), body.get("content")));
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Void> delete(@PathVariable Long messageId, Authentication auth) {
        messageService.deleteMessage(messageId, (Long) auth.getPrincipal());
        return ApiResponse.ok();
    }
}
