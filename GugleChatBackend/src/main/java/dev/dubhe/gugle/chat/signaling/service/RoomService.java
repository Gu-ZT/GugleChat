package dev.dubhe.gugle.chat.signaling.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private final Map<Long, Set<Long>> rooms = new ConcurrentHashMap<>();
    private final Map<Long, Long> userRooms = new ConcurrentHashMap<>();
    private final Map<Long, String> userNames = new ConcurrentHashMap<>();

    public Set<Long> joinRoom(Long roomId, Long userId) {
        Long prev = userRooms.remove(userId);
        if (prev != null && rooms.containsKey(prev)) {
            rooms.get(prev).remove(userId);
            if (rooms.get(prev).isEmpty()) rooms.remove(prev);
        }
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        userRooms.put(userId, roomId);
        Set<Long> others = new HashSet<>(rooms.get(roomId));
        others.remove(userId);
        return others;
    }

    public void setUsername(Long userId, String username) {
        userNames.put(userId, username);
    }

    public String getUsername(Long userId) {
        return userNames.getOrDefault(userId, "User " + userId);
    }

    public Set<Long> leaveRoom(Long userId) {
        Long roomId = userRooms.remove(userId);
        if (roomId != null && rooms.containsKey(roomId)) {
            rooms.get(roomId).remove(userId);
            if (rooms.get(roomId).isEmpty()) rooms.remove(roomId);
            return new HashSet<>(rooms.get(roomId));
        }
        return Collections.emptySet();
    }

    public Set<Long> getRoomMembers(Long roomId) {
        return rooms.getOrDefault(roomId, Collections.emptySet());
    }

    /** Build user list with usernames for frontend display */
    public List<Map<String, Object>> getRoomUsers(Long roomId) {
        return getRoomMembers(roomId).stream()
                .map(uid -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", uid);
                    m.put("username", getUsername(uid));
                    return m;
                })
                .toList();
    }
}
