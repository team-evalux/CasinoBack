// src/main/java/org/example/model/CoinFlipConfig.java
package org.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "coinflip_config")
public class CoinFlipConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // probabilité de "pile" entre 0.0 et 1.0
    @Column(nullable = false)
    private double probPile = 0.5;

    public CoinFlipConfig() {}
    public CoinFlipConfig(double probPile) { this.probPile = probPile; }

    public Long getId() { return id; }
    public double getProbPile() { return probPile; }
    public void setProbPile(double probPile) { this.probPile = Math.max(0.0, Math.min(1.0, probPile)); }
}
