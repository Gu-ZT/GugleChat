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
    private final Map<Long, Long> roomHosts = new ConcurrentHashMap<>();
    private final Map<Long, Double> userQualities = new ConcurrentHashMap<>();

    public void setQuality(Long userId, double quality) {
        userQualities.put(userId, quality);
    }

    public double getQuality(Long userId) {
        return userQualities.getOrDefault(userId, 0.0);
    }

    public Set<Long> joinRoom(Long roomId, Long userId, double quality) {
        userQualities.put(userId, quality);
        Long prev = userRooms.remove(userId);
        if (prev != null && rooms.containsKey(prev)) {
            rooms.get(prev).remove(userId);
            if (rooms.get(prev).isEmpty()) { rooms.remove(prev); roomHosts.remove(prev); }
        }
        rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        userRooms.put(userId, roomId);
        // Pick best quality user as host
        if (!roomHosts.containsKey(roomId) || quality > userQualities.getOrDefault(roomHosts.get(roomId), 0.0)) {
            roomHosts.put(roomId, userId);
        }
        Set<Long> others = new HashSet<>(rooms.get(roomId));
        others.remove(userId);
        return others;
    }

    public Long getHost(Long roomId) {
        return roomHosts.get(roomId);
    }

    public void setUsername(Long userId, String username) {
        userNames.put(userId, username);
    }

    public String getUsername(Long userId) {
        return userNames.getOrDefault(userId, "User " + userId);
    }

    public Set<Long> leaveRoom(Long userId) {
        Long roomId = userRooms.remove(userId);
        userQualities.remove(userId);
        if (roomId != null && rooms.containsKey(roomId)) {
            Set<Long> room = rooms.get(roomId);
            room.remove(userId);
            Set<Long> remaining = new HashSet<>(room);
            if (room.isEmpty()) { rooms.remove(roomId); roomHosts.remove(roomId); }
            // If host left, pick best quality remaining user
            else if (userId.equals(roomHosts.get(roomId))) {
                Long best = remaining.stream()
                        .max(Comparator.comparingDouble(u -> userQualities.getOrDefault(u, 0.0)))
                        .orElse(remaining.iterator().next());
                roomHosts.put(roomId, best);
            }
            return remaining;
        }
        return Collections.emptySet();
    }

    public Set<Long> getActiveRooms() {
        return new HashSet<>(rooms.keySet());
    }

    public Set<Long> getRoomMembers(Long roomId) {
        return rooms.getOrDefault(roomId, Collections.emptySet());
    }

    /** Build user list with usernames and quality for frontend display */
    public List<Map<String, Object>> getRoomUsers(Long roomId) {
        return getRoomMembers(roomId).stream()
                .map(uid -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", uid);
                    m.put("username", getUsername(uid));
                    m.put("quality", getQuality(uid));
                    return m;
                })
                .toList();
    }
}
