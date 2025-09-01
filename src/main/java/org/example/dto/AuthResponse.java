package org.example.dto;

public class AuthResponse {
    public String token;
    public String email;
    public String pseudo;

    public AuthResponse(String token, String email, String pseudo) {
        this.token = token;
        this.email = email;
        this.pseudo = pseudo;
    }
}