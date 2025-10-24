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

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PayoutService {
    private final WalletService wallet;
    private final UtilisateurRepository users;
    private final GameHistoryService history;

    public List<Map<String,Object>> computeAndPay(BjTable t) {
        int dealerTotal = t.getDealer().bestTotal();
        boolean dealerBust = dealerTotal > 21;
        List<Map<String, Object>> pay = new ArrayList<>();

        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            long bet = s.getHand().getBet();
            if (bet <= 0) continue;

            PayoutRules.Outcome o = PayoutRules.compute(s.getHand(), t.getDealer(), bet);
            long credit = o.credit();
            String outcome = o.outcome();

            if (credit > 0) {
                Utilisateur u = users.findByEmail(s.getEmail()).orElseThrow();
                wallet.crediter(u, credit);
            }
            try {
                Utilisateur u = users.findByEmail(s.getEmail()).orElseThrow();
                int multiplier = switch (outcome) { case "BLACKJACK" -> 3; case "WIN" -> 2; case "PUSH" -> 1; default -> 0; };
                history.record(u, "blackjack", "total="+s.getHand().bestTotal()+",outcome="+outcome, bet, credit, multiplier);
            } catch (Exception ignored){}

            pay.add(Map.of("seat", e.getKey(), "bet", bet, "credit", credit, "total", s.getHand().bestTotal(), "outcome", outcome));
        }
        return pay;
    }
}