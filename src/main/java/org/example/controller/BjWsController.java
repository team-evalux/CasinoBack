package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.*;
import org.example.security.JwtUtil;
import org.example.service.BjTableService;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class BjWsController {

    private final BjTableService service;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate broker;

    private String resolveEmail(Principal principal, Message<?> msg) {
        if (principal != null) return principal.getName();

        StompHeaderAccessor acc = StompHeaderAccessor.wrap(msg);
        String token = null;

        List<String> auths = acc.getNativeHeader("Authorization");
        if (auths != null && !auths.isEmpty()) {
            String v = auths.get(0);
            if (v != null && v.startsWith("Bearer ")) token = v.substring(7);
        }
        if (token == null) {
            List<String> toks = acc.getNativeHeader("token");
            if (toks != null && !toks.isEmpty()) token = toks.get(0);
        }
        if (token == null && acc.getSessionAttributes() != null) {
            Object sessTok = acc.getSessionAttributes().get("token");
            if (sessTok instanceof String s && !s.isBlank()) token = s;
        }

        if (token != null && jwtUtil.validerToken(token)) {
            return jwtUtil.extraireSubject(token);
        }
        throw new IllegalStateException("Utilisateur non authentifié sur la socket");
    }

    @MessageMapping("/bj/join")
    public void join(JoinOrCreateMsg msg, Principal principal, Message<?> message) {
        System.out.println("JOIN handler payload=" + msg);
        // extraire headers natifs si disponibles (map String -> List<String>)
        Object nativeHeaders = message.getHeaders().get(SimpMessageHeaderAccessor.NATIVE_HEADERS);
        System.out.println("JOIN raw message.headers.keys=" + message.getHeaders().keySet());
        System.out.println("JOIN nativeHeaders=" + nativeHeaders);

        // try to extract 'code' from native headers when payload doesn't contain it
        String headerCode = null;
        if (nativeHeaders instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, List<String>> nh = (Map<String, List<String>>) nativeHeaders;
                List<String> codes = nh.get("code");
                if (codes != null && !codes.isEmpty()) headerCode = codes.get(0);
            } catch (ClassCastException ignored) {}
        }

        // if msg has no code but header provided one, set it (if setter exists)
        if ((msg.getCode() == null || msg.getCode().isBlank()) && headerCode != null && !headerCode.isBlank()) {
            try {
                msg.setCode(headerCode);
                System.out.println("JOIN: set code from native header into msg: " + headerCode);
            } catch (Throwable t) {
                // fallback: we will pass headerCode explicitly to service if needed
                System.out.println("JOIN: couldn't set code on msg via setter; headerCode=" + headerCode);
            }
        }

        String email = resolveEmail(principal, message);
        try {
            service.joinOrCreate(email, msg);
            System.out.println("JOIN received tableId=" + msg.getTableId() + " code=" + msg.getCode() + " from=" + email);

        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }

    /**
     * sit: on essaie d'extraire un "code" :
     * - en-tête natif STOMP "code"
     * - session attributes["code"]
     * - si le SitMsg a un getCode(), on le lit aussi
     *
     * Si la table est privée on appelle service.authorizeEmailForTable(tableId, email, code)
     */
    @MessageMapping("/bj/sit")
    public void sit(SitMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
            Object nativeHeaders = message.getHeaders().get(SimpMessageHeaderAccessor.NATIVE_HEADERS);
            System.out.println("SIT raw message.headers.keys=" + message.getHeaders().keySet());
            System.out.println("SIT nativeHeaders=" + nativeHeaders);

            String code = null;
            // prefer native header first
            if (nativeHeaders instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, List<String>> nh = (Map<String, List<String>>) nativeHeaders;
                    List<String> ch = nh.get("code");
                    if (ch != null && !ch.isEmpty()) code = ch.get(0);
                } catch (ClassCastException ignored) {}
            }

            // session attribute
            if ((code == null || code.isBlank()) && acc.getSessionAttributes() != null) {
                Object sc = acc.getSessionAttributes().get("code");
                if (sc instanceof String s && !s.isBlank()) code = s;
            }

            // payload code via reflection/getter
            if ((code == null || code.isBlank())) {
                try {
                    java.lang.reflect.Method m = msg.getClass().getMethod("getCode");
                    Object val = m.invoke(msg);
                    if (val instanceof String s && !s.isBlank()) code = s;
                } catch (NoSuchMethodException ignored) {}
            }

            // if msg has no code but we extracted one from headers/session, try to set it on the msg
            if ((msg.getCode() == null || msg.getCode().isBlank()) && code != null && !code.isBlank()) {
                try {
                    java.lang.reflect.Method sm = msg.getClass().getMethod("setCode", String.class);
                    sm.invoke(msg, code);
                    System.out.println("SIT: set code from header/session/payload into msg: " + code);
                } catch (NoSuchMethodException ignored) {
                    // ignore if no setter
                }
            }

            Long tableId = msg.getTableId();
            if (tableId != null) {
                boolean ok = true;
                try {
                    System.out.println("SIT received tableId=" + msg.getTableId() + " seat=" + msg.getSeatIndex() + " code(header/session/payload)=" + code + " from=" + email);
                    ok = service.authorizeEmailForTable(tableId, email, code);
                } catch (Throwable t) {
                    // defensively allow if authorization throws unexpectedly (but log)
                    t.printStackTrace();
                    ok = true;
                }
                if (!ok) {
                    broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", "Code d'accès invalide pour cette table privée"));
                    return;
                }
            }

            service.sit(email, msg.getTableId(), msg.getSeatIndex());
        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }


    @MessageMapping("/bj/bet")
    public void bet(BetMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            service.bet(email, msg);
        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }

    @MessageMapping("/bj/action")
    public void action(ActionMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            service.action(email, msg);
        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }

    @MessageMapping("/bj/leave")
    public void leave(SitMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            service.leave(email, msg.getTableId(), msg.getSeatIndex());
        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }
}
