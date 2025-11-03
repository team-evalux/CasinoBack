package org.example.service.blackjack.registry;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.model.blackjack.BjTable;
import org.example.model.blackjack.BjTableEntity;
import org.example.repo.BjTableRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TableRegistry {
    private final BjTableRepository repo;
    private final Map<Long, BjTable> tables = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadFromDb() {
        for (BjTableEntity e : repo.findAll()) {
            int seats = (e.getMaxSeats() != null) ? e.getMaxSeats() : 5;
            BjTable t = new BjTable(e.getId(), seats, e.isPrivate(), e.getCode());
            t.setName(e.getName());
            t.setMinBet(e.getMinBet());
            t.setMaxBet(e.getMaxBet());
            t.setCreatorEmail(e.getCreatorEmail());
            t.setCreatedAt(e.getCreatedAt());
            t.setLastActiveAt(Instant.now());
            tables.put(t.getId(), t);
        }
    }

    public Collection<BjTable> all() { return tables.values(); }
    public BjTable get(Long id) { BjTable t = tables.get(id); if (t==null) throw new IllegalArgumentException("Table inconnue"); return t; }
    public void put(BjTable t) { tables.put(t.getId(), t); }
    public void remove(Long id) { tables.remove(id); }
    public List<BjTable> listPublic() { return tables.values().stream().filter(t -> !t.isPrivate()).toList(); }

    public BjTable createAndPersist(BjTableEntity ent) {
        var saved = repo.save(ent);
        BjTable t = new BjTable(saved.getId(), Math.max(2, Math.min(7, Optional.ofNullable(saved.getMaxSeats()).orElse(5))), saved.isPrivate(), saved.getCode());
        t.setName(saved.getName()); t.setMinBet(saved.getMinBet()); t.setMaxBet(saved.getMaxBet());
        t.setCreatorEmail(saved.getCreatorEmail()); t.setCreatedAt(saved.getCreatedAt()); t.setLastActiveAt(Instant.now());
        put(t);
        return t;
    }

    public void deleteFromDb(Long id) {
        try {
            repo.deleteById(id);      // <-- ensure this is called
        } catch (Exception e) {
            // log if needed
        }
    }
}
