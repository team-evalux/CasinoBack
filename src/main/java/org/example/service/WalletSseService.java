package org.example.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class WalletSseService {
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String email) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
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
}
