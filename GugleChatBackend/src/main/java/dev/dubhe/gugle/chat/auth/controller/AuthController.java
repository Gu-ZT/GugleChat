package dev.dubhe.gugle.chat.auth.controller;

import dev.dubhe.gugle.chat.auth.dto.AuthResponse;
import dev.dubhe.gugle.chat.auth.dto.LoginRequest;
import dev.dubhe.gugle.chat.auth.dto.RegisterRequest;
import dev.dubhe.gugle.chat.auth.service.AuthService;
import dev.dubhe.gugle.chat.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthResponse.UserInfo> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(authService.getCurrentUser(userId));
    }
}
