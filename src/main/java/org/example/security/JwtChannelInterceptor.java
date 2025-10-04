package org.example.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Intercepteur STOMP pour authentifier la connexion WebSocket via JWT.
 * - Rejette le CONNECT si token absent ou invalide (lancer une exception provoque la fermeture du handshake).
 * - Charge UserDetails pour attacher les autorités (authorities) à l'Authentication.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
        // Permettre modification des headers
        acc.setLeaveMutable(true);

        try {
            if (StompCommand.CONNECT.equals(acc.getCommand())) {
                String token = extractToken(acc);
                if (token == null || !jwtUtil.validerToken(token)) {
                    // Rejette la tentative de connexion : Spring/ broker retournera une erreur au client
                    throw new IllegalArgumentException("Invalid or missing JWT token");
                }
                String email = jwtUtil.extraireSubject(token);
                // charge UserDetails pour récupérer authorities
                UserDetails ud = userDetailsService.loadUserByUsername(email);
                Authentication auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
                acc.setUser(auth);
                // aussi dans le contexte Spring Security (utile pour @PreAuthorize dans handlers)
                SecurityContextHolder.getContext().setAuthentication(auth);
                System.out.println("WebSocket CONNECT - authenticated session " + acc.getSessionId() + " -> " + email);
            } else {
                // pour les autres trames, restaurer le contexte si le user est présent
                var user = acc.getUser();
                if (user instanceof Authentication a) {
                    SecurityContextHolder.getContext().setAuthentication(a);
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
            // renvoyer le message avec les headers modifiés
            return MessageBuilder.createMessage(message.getPayload(), acc.getMessageHeaders());
        } catch (RuntimeException ex) {
            // on propage l'exception pour que le broker refuse le CONNECT ou loggue une erreur.
            throw ex;
        } finally {
            // note: ne pas clear context ici car appelant suivant peut en avoir besoin dans la même thread
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
