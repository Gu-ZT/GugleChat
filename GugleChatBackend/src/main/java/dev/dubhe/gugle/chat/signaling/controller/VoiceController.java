package dev.dubhe.gugle.chat.signaling.controller;

import dev.dubhe.gugle.chat.common.dto.ApiResponse;
import dev.dubhe.gugle.chat.signaling.service.RoomService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api")
public class VoiceController {

    private final RoomService roomService;

    public VoiceController(RoomService roomService) {
        this.roomService = roomService;
    }

    /** Get all rooms with their current voice users */
    @GetMapping("/channels/voice-users")
    public ApiResponse<Map<String, Object>> getVoiceUsers() {
        Map<String, Object> result = new HashMap<>();
        for (Long roomId : roomService.getActiveRooms()) {
            List<Map<String, Object>> users = roomService.getRoomUsers(roomId);
            if (!users.isEmpty()) {
                Map<String, Object> roomInfo = new HashMap<>();
                roomInfo.put("users", users);
                roomInfo.put("hostId", roomService.getHost(roomId));
                result.put(roomId.toString(), roomInfo);
            }
        }
        return ApiResponse.ok(result);
    }
}
