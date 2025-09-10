// src/main/java/org/example/model/RouletteConfig.java
package org.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "roulette_config")
public class RouletteConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JSON serialized map numéro -> poids
    @Lob
    @Column(name = "weights_json", columnDefinition = "text")
    private String weightsJson;

    public RouletteConfig() {}
    public RouletteConfig(String weightsJson) { this.weightsJson = weightsJson; }

    public Long getId() { return id; }
    public String getWeightsJson() { return weightsJson; }
    public void setWeightsJson(String weightsJson) { this.weightsJson = weightsJson; }
}
