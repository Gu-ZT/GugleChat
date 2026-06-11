package dev.dubhe.gugle.chat.auth.service;

import dev.dubhe.gugle.chat.auth.dto.AuthResponse;
import dev.dubhe.gugle.chat.auth.dto.LoginRequest;
import dev.dubhe.gugle.chat.auth.dto.RegisterRequest;
import dev.dubhe.gugle.chat.auth.model.User;
import dev.dubhe.gugle.chat.auth.model.UserMapper;
import dev.dubhe.gugle.chat.common.exception.BusinessException;
import dev.dubhe.gugle.chat.common.service.OnlineStatusService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OnlineStatusService onlineStatusService;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       OnlineStatusService onlineStatusService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.onlineStatusService = onlineStatusService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userMapper.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already taken");
        }
        if (userMapper.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }

        User user = new User(request.getUsername(), request.getEmail(),
                passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getUsername());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResponse(token, toUserInfo(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException("Invalid username or password");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid username or password");
        }

        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        onlineStatusService.heartbeat(user.getId(), user.getUsername());

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name());
        return new AuthResponse(token, toUserInfo(user));
    }

    public AuthResponse.UserInfo getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("User not found");
        return toUserInfo(user);
    }

    private AuthResponse.UserInfo toUserInfo(User user) {
        return new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getNickname(), user.getAvatarUrl(), user.getRole().name());
    }
}
