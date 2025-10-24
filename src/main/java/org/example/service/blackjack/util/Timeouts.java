package org.example.service.blackjack.util;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class Timeouts {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    // groupKey = tableId, name = logical timer name
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public void schedule(Long tableId, String name, long delayMs, Runnable task) {
        cancel(tableId, name);
        String key = key(tableId, name);
        tasks.put(key, scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS));
    }

    public void cancel(Long tableId, String name) {
        String key = key(tableId, name);
        ScheduledFuture<?> f = tasks.remove(key);
        if (f != null) f.cancel(false);
    }

    public void cancelAllOf(Long tableId) {
        tasks.keySet().removeIf(k -> {
            if (k.startsWith(tableId + ":")) {
                ScheduledFuture<?> f = tasks.remove(k);
                if (f != null) f.cancel(false);
                return true;
            }
            return false;
        });
    }

    private String key(Long tableId, String name) { return tableId + ":" + name; }
}
