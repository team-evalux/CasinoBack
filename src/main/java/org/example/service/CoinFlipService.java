package org.example.service;

import org.example.model.Utilisateur;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class CoinFlipService {
    // probabilité que le résultat soit "pile" (entre 0.0 et 1.0)
    private volatile double probPile;

    private final SecureRandom random = new SecureRandom();

    public CoinFlipService(@Value("${coinflip.probPile:0.5}") double probPile) {
        this.probPile = Math.max(0.0, Math.min(1.0, probPile));
    }

    // tirage respectant la probabilité courante
    // renvoie "pile" ou "face"
    public String tirer() {
        double v = random.nextDouble();
        return v < probPile ? "pile" : "face";
    }

    public double getProbPile() {
        return probPile;
    }

    public void setProbPile(double probPile) {
        this.probPile = Math.max(0.0, Math.min(1.0, probPile));
    }
}
