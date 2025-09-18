// src/main/java/org/example/security/JwtHandshakeInterceptor.java
package org.example.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest http) {
            HttpServletRequest req = http.getServletRequest();

            // 1) token via query param ?token=...
            String token = req.getParameter("token");

            // 2) sinon header Authorization: Bearer ...
            if (token == null) {
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }

            // 3) stocker dans les attributes de session WS
            if (token != null && !token.isBlank()) {
                attributes.put("token", token);
            }
        }
        return true; // OK pour poursuivre le handshake
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               @Nullable Exception ex) {
        // rien à faire
    }
}
