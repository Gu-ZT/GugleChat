package dev.dubhe.gugle.chat.message.controller;

import com.guglechat.common.dto.ApiResponse;
import com.guglechat.message.dto.MessageResponse;
import com.guglechat.message.service.MessageService;
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
                                                          @RequestParam(required = false) Long before) {
        return ApiResponse.ok(messageService.getHistory(channelId, before));
    }

    @PutMapping("/messages/{messageId}")
    public ApiResponse<MessageResponse> edit(@PathVariable Long messageId,
                                              @RequestBody Map<String, String> body,
                                              Authentication auth) {
        return ApiResponse.ok(messageService.editMessage(messageId, (Long) auth.getPrincipal(), body.get("content")));
    }

    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<Void> delete(@PathVariable Long messageId, Authentication auth) {
        messageService.deleteMessage(messageId, (Long) auth.getPrincipal());
        return ApiResponse.ok();
    }
}
