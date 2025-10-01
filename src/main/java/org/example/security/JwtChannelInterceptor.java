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

@Component // Intercepteur pour sécuriser les messages WebSocket STOMP
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
        acc.setLeaveMutable(true); // permet de modifier les headers

        try {
            if (StompCommand.CONNECT.equals(acc.getCommand())) {
                // Lors de la connexion WS
                String token = extractToken(acc);
                if (token != null && jwtUtil.validerToken(token)) {
                    String email = jwtUtil.extraireSubject(token);
                    // Crée un Principal avec l’email comme identifiant
                    var auth = new UsernamePasswordAuthenticationToken(email, null, List.of());
                    acc.setUser(auth);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    System.out.println("WebSocket CONNECT OK : " + email);
                } else {
                    System.out.println("WebSocket CONNECT refusé : token manquant/invalide");
                }
            } else {
                // Pour les autres messages, restaure le contexte si le user existe
                var user = acc.getUser();
                if (user != null) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user.getName(), null, List.of())
                    );
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
            // Retourne le message avec headers modifiés
            return MessageBuilder.createMessage(message.getPayload(), acc.getMessageHeaders());
        } catch (Exception ex) {
            // En cas d’erreur, ne casse pas la connexion
            return message;
        }
    }

    // Méthode utilitaire pour extraire le token depuis les headers STOMP
    private String extractToken(StompHeaderAccessor acc) {
        // 1) Authorization: Bearer ...
        List<String> auths = acc.getNativeHeader("Authorization");
        if (auths != null && !auths.isEmpty()) {
            String v = auths.get(0);
            if (v != null && v.startsWith("Bearer ")) return v.substring(7);
        }
        // 2) Header STOMP "token"
        List<String> toks = acc.getNativeHeader("token");
        if (toks != null && !toks.isEmpty()) return toks.get(0);

        // 3) Token stocké par JwtHandshakeInterceptor
        var attrs = acc.getSessionAttributes();
        if (attrs != null) {
            Object sessTok = attrs.get("token");
            if (sessTok instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }
}
