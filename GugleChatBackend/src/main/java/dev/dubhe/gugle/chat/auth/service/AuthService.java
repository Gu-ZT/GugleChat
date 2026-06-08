package dev.dubhe.gugle.chat.auth.service;

import com.guglechat.auth.dto.AuthResponse;
import com.guglechat.auth.dto.LoginRequest;
import com.guglechat.auth.dto.RegisterRequest;
import com.guglechat.auth.model.User;
import com.guglechat.auth.model.UserRepository;
import com.guglechat.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered");
        }

        User user = new User(request.getUsername(), request.getEmail(),
                passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getUsername());
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, toUserInfo(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid username or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new AuthResponse(token, toUserInfo(user));
    }

    public AuthResponse.UserInfo getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));
        return toUserInfo(user);
    }

    private AuthResponse.UserInfo toUserInfo(User user) {
        return new AuthResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getNickname(), user.getAvatarUrl());
    }
}
