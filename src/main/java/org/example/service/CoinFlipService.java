// src/main/java/org/example/service/CoinFlipService.java
package org.example.service;

import org.example.model.CoinFlipConfig;
import org.example.repo.CoinFlipConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.util.List;

@Service
public class CoinFlipService {
    private volatile double probPile;
    private final SecureRandom random = new SecureRandom();
    private final CoinFlipConfigRepository repo;

    public CoinFlipService(CoinFlipConfigRepository repo, @Value("${coinflip.probPile:0.5}") double initialProb) {
        this.repo = repo;
        this.probPile = Math.max(0.0, Math.min(1.0, initialProb));
    }

    @PostConstruct
    public void initFromDb() {
        List<CoinFlipConfig> all = repo.findAll();
        if (all.isEmpty()) {
            CoinFlipConfig cfg = new CoinFlipConfig(probPile);
            repo.save(cfg);
        } else {
            // prends la première config existante
            this.probPile = all.get(0).getProbPile();
        }
    }

    public String tirer() {
        double v = random.nextDouble();
        return v < probPile ? "pile" : "face";
    }

    public double getProbPile() { return probPile; }

    // Persist la nouvelle prob et met à jour la DB (met à jour la première ligne)
    public synchronized void setProbPile(double probPile) {
        this.probPile = Math.max(0.0, Math.min(1.0, probPile));
        List<CoinFlipConfig> all = repo.findAll();
        if (all.isEmpty()) {
            repo.save(new CoinFlipConfig(this.probPile));
        } else {
            CoinFlipConfig cfg = all.get(0);
            cfg.setProbPile(this.probPile);
            repo.save(cfg);
        }
    }
}
