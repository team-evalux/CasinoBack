// src/main/java/org/example/model/SlotConfig.java
package org.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "slot_config")
public class SlotConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JSON: symbols -> ["🍒","..."]
    @Lob
    @Column(name = "symbols_json", columnDefinition = "text")
    private String symbolsJson;

    // JSON: reelWeights -> [[100,100,...],[...],...]
    @Lob
    @Column(name = "reel_weights_json", columnDefinition = "text")
    private String reelWeightsJson;

    // JSON: payouts -> {"3":10,"2":2}
    @Lob
    @Column(name = "payouts_json", columnDefinition = "text")
    private String payoutsJson;

    @Column(name = "reels_count")
    private Integer reelsCount;

    public SlotConfig() {}
    public SlotConfig(String symbolsJson, String reelWeightsJson, String payoutsJson, Integer reelsCount) {
        this.symbolsJson = symbolsJson;
        this.reelWeightsJson = reelWeightsJson;
        this.payoutsJson = payoutsJson;
        this.reelsCount = reelsCount;
    }

    // getters / setters
    public Long getId() { return id; }
    public String getSymbolsJson() { return symbolsJson; }
    public void setSymbolsJson(String symbolsJson) { this.symbolsJson = symbolsJson; }
    public String getReelWeightsJson() { return reelWeightsJson; }
    public void setReelWeightsJson(String reelWeightsJson) { this.reelWeightsJson = reelWeightsJson; }
    public String getPayoutsJson() { return payoutsJson; }
    public void setPayoutsJson(String payoutsJson) { this.payoutsJson = payoutsJson; }
    public Integer getReelsCount() { return reelsCount; }
    public void setReelsCount(Integer reelsCount) { this.reelsCount = reelsCount; }
}
