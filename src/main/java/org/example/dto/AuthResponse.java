package org.example.dto;

public class AuthResponse {
    public String token;
    public String email;
    public String pseudo;
    public String role;

    public AuthResponse(String token, String email, String pseudo, String role) {
        this.token = token;
        this.email = email;
        this.pseudo = pseudo;
        this.role = role;
    }
}
