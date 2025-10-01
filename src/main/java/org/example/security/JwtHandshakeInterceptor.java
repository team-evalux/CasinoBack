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

            // 1) Cherche un token dans les paramètres de l’URL (?token=...)
            String token = req.getParameter("token");

            // 2) Sinon, tente dans l’entête HTTP Authorization
            if (token == null) {
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    token = auth.substring(7);
                }
            }

            // 3) Si trouvé, le stocke dans les attributs de la session WebSocket
            if (token != null && !token.isBlank()) {
                attributes.put("token", token);
            }
        }
        return true; // continue le handshake
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               @Nullable Exception ex) {
        // rien à faire après
    }
}
