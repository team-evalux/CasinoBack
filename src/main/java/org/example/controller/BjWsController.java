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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class BjWsController {

    private final BjTableService service;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate broker;

    private final Map<Long, Object> tableLocks = new ConcurrentHashMap<>();
    private Object getTableLock(Long tableId) {
        return tableLocks.computeIfAbsent(tableId, id -> new Object());
    }

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

    // ----------------------------------------------------------------
    // JOINDRE (ou CRÉER) = entrer + auto-seat (refus si table pleine)
    @MessageMapping("/bj/join")
    public void join(JoinOrCreateMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            Object nativeHeaders = message.getHeaders().get(SimpMessageHeaderAccessor.NATIVE_HEADERS);
            String headerCode = null;

            if (nativeHeaders instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, List<String>> nh = (Map<String, List<String>>) nativeHeaders;
                    List<String> codes = nh.get("code");
                    if (codes != null && !codes.isEmpty()) headerCode = codes.get(0);
                } catch (ClassCastException ignored) {}
            }

            if ((msg.getCode() == null || msg.getCode().isBlank()) && headerCode != null && !headerCode.isBlank()) {
                msg.setCode(headerCode);
            }

            Long lockId = msg.getTableId() != null ? Long.valueOf(msg.getTableId().toString()) : -1L;
            synchronized (getTableLock(lockId)) {
                service.joinOrCreate(email, msg); // fait l’enter + auto-seat en interne
                System.out.println("JOIN reçu -> tableId=" + msg.getTableId() + ", code=" + msg.getCode() + ", from=" + email);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors",
                    Map.of("error", ex.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    // /bj/sit : compat → no-op fonctionnel (re-passe par enter)
    @MessageMapping("/bj/sit")
    public void sit(SitMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            StompHeaderAccessor acc = StompHeaderAccessor.wrap(message);
            Object nativeHeaders = message.getHeaders().get(SimpMessageHeaderAccessor.NATIVE_HEADERS);

            String code = null;
            if (nativeHeaders instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, List<String>> nh = (Map<String, List<String>>) nativeHeaders;
                    List<String> ch = nh.get("code");
                    if (ch != null && !ch.isEmpty()) code = ch.get(0);
                } catch (ClassCastException ignored) {}
            }
            if ((code == null || code.isBlank()) && acc.getSessionAttributes() != null) {
                Object sc = acc.getSessionAttributes().get("code");
                if (sc instanceof String s && !s.isBlank()) code = s;
            }
            if ((msg.getCode() == null || msg.getCode().isBlank()) && code != null && !code.isBlank()) {
                try { msg.getClass().getMethod("setCode", String.class).invoke(msg, code); } catch (Exception ignored) {}
            }

            Long tableId = msg.getTableId();
            if (tableId == null) {
                broker.convertAndSendToUser(email, "/queue/bj/errors",
                        Map.of("error", "Table inconnue ou invalide."));
                return;
            }

            synchronized (getTableLock(tableId)) {
                // On garde la vérification d’accès pour les privées (compat)
                boolean ok = service.authorizeEmailForTable(tableId, email, msg.getCode());
                if (!ok) {
                    broker.convertAndSendToUser(email, "/queue/bj/errors",
                            Map.of("error", "Code d'accès invalide pour cette table privée"));
                    return;
                }
                // No-op réel (auto-seat déjà fait au JOIN) — mais idempotent:
                service.sit(email, tableId, msg.getSeatIndex());
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors",
                    Map.of("error", ex.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    @MessageMapping("/bj/bet")
    public void bet(BetMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            synchronized (getTableLock(msg.getTableId() != null ? Long.valueOf(msg.getTableId().toString()) : -1L)) {
                service.bet(email, msg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors",
                    Map.of("error", ex.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    @MessageMapping("/bj/action")
    public void action(ActionMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            synchronized (getTableLock(msg.getTableId() != null ? Long.valueOf(msg.getTableId().toString()) : -1L)) {
                service.action(email, msg);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors",
                    Map.of("error", ex.getMessage()));
        }
    }

    // ----------------------------------------------------------------
    @MessageMapping("/bj/leave")
    public void leave(SitMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            synchronized (getTableLock(msg.getTableId() != null ? Long.valueOf(msg.getTableId().toString()) : -1L)) {
                service.leave(email, msg.getTableId(), msg.getSeatIndex());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            broker.convertAndSendToUser(email, "/queue/bj/errors",
                    Map.of("error", ex.getMessage()));
        }
    }
}
