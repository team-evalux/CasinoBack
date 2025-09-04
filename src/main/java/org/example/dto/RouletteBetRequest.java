package org.example.dto;

public class RouletteBetRequest {
    public String betType; // "straight" | "color" | "parity" | "range" | "dozen"
    public String betValue; // for straight: "17", for color: "red"/"black", parity: "even"/"odd", range: "low"/"high", dozen: "1"/"2"/"3"
    public long montant;

    public RouletteBetRequest() {}
}
