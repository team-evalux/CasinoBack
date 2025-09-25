// src/main/java/org/example/security/JwtChannelInterceptor.java
package org.example.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
        // Permettre modification des headers
        acc.setLeaveMutable(true);

        try {
            if (StompCommand.CONNECT.equals(acc.getCommand())) {
                String token = extractToken(acc);
                if (token != null && jwtUtil.validerToken(token)) {
                    String email = jwtUtil.extraireSubject(token);
                    // créer un Principal simple avec le nom = email
                    var auth = new UsernamePasswordAuthenticationToken(email, null, List.of());
                    acc.setUser(auth);
                    // pour le contexte Spring Security (utile si tu utilises des méthodes sécurisées)
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    System.out.println("WebSocket CONNECT - user set for session " + acc.getSessionId() + " -> " + email);
                } else {
                    System.out.println("WebSocket CONNECT - no/invalid token for session " + acc.getSessionId());
                }
            } else {
                // pour les autres trames, restaurer le contexte si le user est présent
                var user = acc.getUser();
                if (user != null) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user.getName(), null, List.of())
                    );
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
            // renvoyer le message avec le headers modifiés
            return MessageBuilder.createMessage(message.getPayload(), acc.getMessageHeaders());
        } catch (Exception ex) {
            // en cas d'erreur ne casse pas la connexion, renvoie le message original
            return message;
        }
    }

    private String extractToken(StompHeaderAccessor acc) {
        // 1) Authorization: Bearer ...
        List<String> auths = acc.getNativeHeader("Authorization");
        if (auths != null && !auths.isEmpty()) {
            String v = auths.get(0);
            if (v != null && v.startsWith("Bearer ")) return v.substring(7);
        }
        // 2) native header "token"
        List<String> toks = acc.getNativeHeader("token");
        if (toks != null && !toks.isEmpty()) return toks.get(0);

        // 3) fallback : token stocké par le handshake interceptor dans session attributes
        var attrs = acc.getSessionAttributes();
        if (attrs != null) {
            Object sessTok = attrs.get("token");
            if (sessTok instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }
}
