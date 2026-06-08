package dev.dubhe.gugle.chat.channel.controller;

import dev.dubhe.gugle.chat.channel.dto.*;
import dev.dubhe.gugle.chat.channel.service.ChannelService;
import dev.dubhe.gugle.chat.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) { this.channelService = channelService; }

    @GetMapping
    public ApiResponse<List<ChannelResponse>> list(Authentication auth) {
        return ApiResponse.ok(channelService.getAllChannels((Long) auth.getPrincipal()));
    }

    @PostMapping
    public ApiResponse<ChannelResponse> create(@Valid @RequestBody ChannelRequest req, Authentication auth) {
        return ApiResponse.ok(channelService.createChannel((Long) auth.getPrincipal(), req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChannelResponse> update(@PathVariable Long id, @Valid @RequestBody ChannelRequest req, Authentication auth) {
        return ApiResponse.ok(channelService.updateChannel(id, (Long) auth.getPrincipal(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication auth) {
        channelService.deleteChannel(id, (Long) auth.getPrincipal());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<MemberResponse>> members(@PathVariable Long id, Authentication auth) {
        return ApiResponse.ok(channelService.getMembers(id, (Long) auth.getPrincipal()));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<Void> addMember(@PathVariable Long id, @RequestBody Map<String, Long> body, Authentication auth) {
        channelService.addMember(id, (Long) auth.getPrincipal(), body.get("userId"));
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        channelService.removeMember(id, (Long) auth.getPrincipal(), userId);
        return ApiResponse.ok();
    }
}
