package org.example.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class WalletSseService {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String email) {
        // 30 minutes : suffisant et plus réaliste que Long.MAX_VALUE derrière proxy
        SseEmitter emitter = new SseEmitter(6L * 60 * 60 * 1000L);
        emitters.computeIfAbsent(email, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(email, emitter));
        emitter.onTimeout(() -> removeEmitter(email, emitter));
        emitter.onError((e) -> removeEmitter(email, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ignored) {}

        return emitter;
    }

    private void removeEmitter(String email, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(email);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(email);
        }
    }

    public void sendBalanceUpdate(String email, long solde) {
        List<SseEmitter> list = emitters.get(email);
        if (list == null) return;

        for (SseEmitter emitter : list.toArray(new SseEmitter[0])) {
            try {
                emitter.send(SseEmitter.event()
                        .name("wallet-update")
                        .data(Map.of("solde", solde)));
            } catch (IOException e) {
                removeEmitter(email, emitter);
            }
        }
    }

    // Heartbeat toutes les 15s pour garder le flux ouvert derrière Nginx/proxies
    @Scheduled(fixedDelay = 15000)
    public void heartbeat() {
        for (var entry : emitters.entrySet()) {
            String email = entry.getKey();
            for (SseEmitter emitter : entry.getValue().toArray(new SseEmitter[0])) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("keepalive"));
                } catch (IOException e) {
                    removeEmitter(email, emitter);
                }
            }
        }
    }

    /** Ferme tous les SSE d’un utilisateur (utilisé avant suppression) */
    public void complete(String email) {
        List<SseEmitter> list = emitters.remove(email);
        if (list == null) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception ignored) {

            }
        }
    }
}
