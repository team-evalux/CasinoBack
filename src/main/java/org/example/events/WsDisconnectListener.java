package org.example.events;

import lombok.RequiredArgsConstructor;
import org.example.service.BjTableService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class WsDisconnectListener {

    private final BjTableService service;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        Principal p = e.getUser();
        if (p != null) service.markDisconnected(p.getName());
    }

    @EventListener
    public void onConnect(SessionConnectEvent e) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(e.getMessage());
        Principal p = acc.getUser();
        if (p != null) service.markReconnected(p.getName());
    }
}
