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
        acc.setLeaveMutable(true); // ✅ IMPORTANT

        if (StompCommand.CONNECT.equals(acc.getCommand())) {
            String token = extractToken(acc);
            if (token != null && jwtUtil.validerToken(token)) {
                String email = jwtUtil.extraireSubject(token);
                var auth = new UsernamePasswordAuthenticationToken(email, null, List.of());
                acc.setUser(auth);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } else {
            var user = acc.getUser();
            if (user != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user.getName(), null, List.of())
                );
            }
        }
        // ✅ renvoyer le message avec headers mis à jour
        return MessageBuilder.createMessage(message.getPayload(), acc.getMessageHeaders());
    }

    private String extractToken(StompHeaderAccessor acc) {
        List<String> auths = acc.getNativeHeader("Authorization");
        if (auths != null && !auths.isEmpty()) {
            String v = auths.get(0);
            if (v != null && v.startsWith("Bearer ")) return v.substring(7);
        }
        List<String> toks = acc.getNativeHeader("token");
        if (toks != null && !toks.isEmpty()) return toks.get(0);

        // fallback via handshake interceptor (voir B.2)
        var attrs = acc.getSessionAttributes();
        if (attrs != null) {
            Object sessTok = attrs.get("token");
            if (sessTok instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }
}
