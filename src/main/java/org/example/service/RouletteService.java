package org.example.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Set;

@Service
public class RouletteService {
    private final SecureRandom random = new SecureRandom();

    // standard roulette red numbers
    private static final Set<Integer> RED = Set.of(
            1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36
    );

    public int tirerNumero() {
        // 0..36 inclusive
        return random.nextInt(37);
    }

    public String couleurPour(int number) {
        if (number == 0) return "green";
        return RED.contains(number) ? "red" : "black";
    }

    /**
     * Calcule si le pari (type+value) est gagnant pour le numéro tiré.
     * Retourne true si gagnant.
     */
    public boolean estGagnant(String betType, String betValue, int numero) {
        if ("straight".equals(betType)) {
            try {
                int n = Integer.parseInt(betValue);
                return n == numero;
            } catch (NumberFormatException e) { return false; }
        }
        if ("color".equals(betType)) {
            String color = couleurPour(numero);
            return betValue != null && betValue.equalsIgnoreCase(color);
        }
        if ("parity".equals(betType)) {
            if (numero == 0) return false;
            boolean isEven = (numero % 2 == 0);
            return ("even".equalsIgnoreCase(betValue) && isEven) ||
                    ("odd".equalsIgnoreCase(betValue) && !isEven);
        }
        if ("range".equals(betType)) {
            if ("low".equalsIgnoreCase(betValue)) return numero >= 1 && numero <= 18;
            if ("high".equalsIgnoreCase(betValue)) return numero >= 19 && numero <= 36;
            return false;
        }
        if ("dozen".equals(betType)) {
            if ("1".equals(betValue)) return numero >=1 && numero <= 12;
            if ("2".equals(betValue)) return numero >=13 && numberInRange(numero,13,24);
            if ("3".equals(betValue)) return numero >=25 && numero <= 36;
            // fallback: try numeric
            try {
                int d = Integer.parseInt(betValue);
                if (d == 1) return numero >= 1 && numero <=12;
                if (d == 2) return numero >=13 && numero <=24;
                if (d == 3) return numero >=25 && numero <=36;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private boolean numberInRange(int n, int a, int b) {
        return n >= a && n <= b;
    }

    /**
     * Retourne le multiplicateur (pour calculer le retour total = montant * multiplier).
     * Ex : straight => 36 (mise * 36), color => 2 (mise * 2)
     */
    public long payoutMultiplier(String betType) {
        switch (betType) {
            case "straight": return 36L; // 35:1 net -> total 36x
            case "dozen": return 3L;     // 2:1 net -> total 3x
            case "color": case "parity": case "range": return 2L; // 1:1 -> total 2x
            default: return 0L;
        }
    }
}
