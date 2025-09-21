// src/main/java/org/example/controller/BjWsController.java
package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.*;
import org.example.security.JwtUtil;
import org.example.service.BjTableService;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

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
        String email = resolveEmail(principal, message);
        try {
            service.joinOrCreate(email, msg);
        } catch (Exception ex) {
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }

    @MessageMapping("/bj/sit")
    public void sit(SitMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            service.sit(email, msg.getTableId(), msg.getSeatIndex());
        } catch (Exception ex) {
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }

    @MessageMapping("/bj/bet")
    public void bet(BetMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            service.bet(email, msg);
        } catch (Exception ex) {
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }

    @MessageMapping("/bj/action")
    public void action(ActionMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            service.action(email, msg);
        } catch (Exception ex) {
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }

    @MessageMapping("/bj/leave")
    public void leave(SitMsg msg, Principal principal, Message<?> message) {
        String email = resolveEmail(principal, message);
        try {
            service.leave(email, msg.getTableId(), msg.getSeatIndex());
        } catch (Exception ex) {
            broker.convertAndSendToUser(email, "/queue/bj/errors", java.util.Map.of("error", ex.getMessage()));
        }
    }
}
