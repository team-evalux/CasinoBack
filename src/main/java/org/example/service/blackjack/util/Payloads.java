package org.example.service.blackjack.util;

import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Payloads {

    // Fallback local (sans DB)
    private String emailLocalPart(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
    private final UtilisateurRepository utilisateurRepo;
    public Payloads(UtilisateurRepository utilisateurRepo) { this.utilisateurRepo = utilisateurRepo; }

    private String displayNameForEmail(String email) {
        if (email == null) return null;
        try {
            return utilisateurRepo.findByEmail(email).map(Utilisateur::getPseudo)
                    .orElseGet(() -> {
                        int at = email.indexOf('@');
                        return at > 0 ? email.substring(0, at) : email;
                    });
        } catch (Exception e) {
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
    }

    public Map<Integer, Map<String,Object>> seatsPayload(BjTable t) {
        Map<Integer, Map<String,Object>> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Integer idx = e.getKey();
            Seat s = e.getValue();
            Map<String,Object> seatMap = new LinkedHashMap<>();
            if (s != null && s.getStatus() != null && s.getStatus() != SeatStatus.EMPTY) {
                seatMap.put("userId", s.getUserId());
                seatMap.put("email", s.getEmail());
                // ✅ n’utilise plus le repo : lit ce qui est déjà posé dans Seat
                String dn = (s.getDisplayName() != null && !s.getDisplayName().isBlank())
                        ? s.getDisplayName()
                        : emailLocalPart(s.getEmail());
                seatMap.put("displayName", dn);
                seatMap.put("status", s.getStatus().name());

                Map<String,Object> handMap = new LinkedHashMap<>();
                handMap.put("cards", s.getHand().getCards());
                handMap.put("standing", s.getHand().isStanding());
                handMap.put("busted", s.getHand().isBusted());
                handMap.put("blackjack", s.getHand().isBlackjack());
                handMap.put("bet", s.getHand().getBet());
                handMap.put("total", s.getHand().bestTotal());
                seatMap.put("hand", handMap);
            } else {
                seatMap.put("status", "EMPTY");
                seatMap.put("hand", Map.of(
                        "cards", List.of(),
                        "standing", false,
                        "busted", false,
                        "blackjack", false,
                        "bet", 0,
                        "total", 0
                ));
            }
            out.put(idx, seatMap);
        }
        return out;
    }

    public Map<String,Object> tableState(BjTable t, long deadline) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("tableId", t.getId());
        m.put("phase", t.getPhase());
        m.put("deadline", deadline);
        m.put("seats", seatsPayload(t));
        m.put("dealer", t.getDealer());
        m.put("currentSeatIndex", t.getCurrentSeatIndex());
        m.put("isPrivate", t.isPrivate());
        m.put("creatorEmail", t.getCreatorEmail());
        m.put("creatorDisplayName", displayNameForEmail(t.getCreatorEmail()));
        m.put("name", t.getName());
        m.put("minBet", t.getMinBet());
        m.put("maxBet", t.getMaxBet());
        return m;
    }
}
