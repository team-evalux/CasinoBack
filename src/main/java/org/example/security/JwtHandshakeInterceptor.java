// src/main/java/org/example/security/JwtHandshakeInterceptor.java
package org.example.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws IOException {
        if (request instanceof ServletServerHttpRequest http) {
            HttpServletRequest req = http.getServletRequest();

            // 1) Récupère le token dans l’ordre de priorité
            String token = req.getParameter("token");
            if (token == null) {
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }

            // 2) Vérifie la validité
            if (token == null || token.isBlank() || !jwtUtil.validerToken(token)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false; // ⛔ stoppe la connexion
            }
            // 3) Stocke le token dans la session WS (utilisable plus tard)
            attributes.put("token", token);
        }

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               @Nullable Exception ex) {
    }
}
