package dev.dubhe.gugle.chat.signaling.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    // roomId (channelId) -> set of userIds
    private final Map<Long, Set<Long>> rooms = new ConcurrentHashMap<>();
    // userId -> channelId
    private final Map<Long, Long> userRooms = new ConcurrentHashMap<>();

    public Set<Long> joinRoom(Long roomId, Long userId) {
        // Leave previous room if any
        Long prev = userRooms.remove(userId);
        if (prev != null && rooms.containsKey(prev)) {
            rooms.get(prev).remove(userId);
            if (rooms.get(prev).isEmpty()) rooms.remove(prev);
        }

        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        userRooms.put(userId, roomId);

        // Return all OTHER users in the room
        Set<Long> others = new HashSet<>(rooms.get(roomId));
        others.remove(userId);
        return others;
    }

    public Set<Long> leaveRoom(Long userId) {
        Long roomId = userRooms.remove(userId);
        if (roomId != null && rooms.containsKey(roomId)) {
            rooms.get(roomId).remove(userId);
            Set<Long> remaining = new HashSet<>(rooms.get(roomId));
            if (rooms.get(roomId).isEmpty()) rooms.remove(roomId);
            return remaining;
        }
        return Collections.emptySet();
    }

    public Set<Long> getRoomMembers(Long roomId) {
        return rooms.getOrDefault(roomId, Collections.emptySet());
    }
}
