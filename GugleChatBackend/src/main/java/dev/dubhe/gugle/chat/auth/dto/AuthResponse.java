package dev.dubhe.gugle.chat.auth.dto;

public class AuthResponse {
    private String token;
    private String tokenType = "Bearer";
    private UserInfo user;

    public AuthResponse(String token, UserInfo user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public String getTokenType() { return tokenType; }
    public UserInfo getUser() { return user; }

    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String nickname;
        private String avatarUrl;

        public UserInfo(Long id, String username, String email, String nickname, String avatarUrl) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.nickname = nickname;
            this.avatarUrl = avatarUrl;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getNickname() { return nickname; }
        public String getAvatarUrl() { return avatarUrl; }
    }
}
