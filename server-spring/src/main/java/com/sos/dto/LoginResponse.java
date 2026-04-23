package com.sos.dto;

public class LoginResponse {
    private String token;
    private UserInfo user;

    public LoginResponse(String token, UserInfo user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public UserInfo getUser() { return user; }

    public static class UserInfo {
        private Integer id;
        private String name;
        private String username;
        private String role;

        public UserInfo(Integer id, String name, String username, String role) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.role = role;
        }

        public Integer getId() { return id; }
        public String getName() { return name; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
    }
}
