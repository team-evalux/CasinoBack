package org.example.service.blackjack.engine;

import lombok.RequiredArgsConstructor;
import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.model.blackjack.rules.DealingRules;
import org.example.model.blackjack.rules.PayoutRules;
import org.example.repo.UtilisateurRepository;
import org.example.service.GameHistoryService;
import org.example.service.WalletService;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Payloads;
import org.example.service.blackjack.util.Timeouts;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// src/main/java/org/example/service/blackjack/engine/PayoutService.java
@Service
@RequiredArgsConstructor
public class PayoutService {
    private final WalletService wallet;
    private final UtilisateurRepository users;
    private final GameHistoryService history;

    @Transactional
    public List<Map<String,Object>> computeAndPay(BjTable t) {
        List<Map<String, Object>> pay = new ArrayList<>();

        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            final Integer seatIndex = e.getKey();
            final Seat s = e.getValue();
            final long bet = s.getHand().getBet();
            if (bet <= 0) continue;

            var o = PayoutRules.compute(s.getHand(), t.getDealer(), bet);
            final long credit = o.credit();
            final String outcome = o.outcome();

            // 1 seul lookup utilisateur
            Utilisateur u = users.findByEmail(s.getEmail()).orElse(null);

            // Paiement (si utilisateur trouvé)
            if (credit > 0 && u != null) {
                try { wallet.crediter(u, credit); }
                catch (Exception ex) { /* log.warn si tu veux */ }
            }

            // Historique (même si crédit 0) — protège si u == null
            if (u != null) {
                int multiplier = switch (outcome) {
                    case "BLACKJACK" -> 3;
                    case "WIN"       -> 2;
                    case "PUSH"      -> 1;
                    default          -> 0;
                };
                try {
                    history.record(
                            u,
                            "blackjack",
                            "total="+s.getHand().bestTotal()+",outcome="+outcome,
                            bet,
                            credit,
                            multiplier
                    );
                } catch (Exception ex) { /* log.warn si besoin */ }
            }

            pay.add(Map.of(
                    "seat", seatIndex,
                    "bet", bet,
                    "credit", credit,
                    "total", s.getHand().bestTotal(),
                    "outcome", outcome
            ));
        }
        return pay;
    }
}
