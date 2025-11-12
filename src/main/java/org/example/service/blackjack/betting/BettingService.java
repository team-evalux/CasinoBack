package org.example.service.blackjack.betting;

import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.BetMsg;
import org.example.model.blackjack.*;
import org.example.service.blackjack.registry.TableRegistry;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BettingService {
    private final TableRegistry registry;

    // BettingService.java
    public void bet(String email, BetMsg msg) {
        BjTable t = registry.get(msg.getTableId());
        if (t.getPhase() != TablePhase.BETTING) throw new IllegalStateException("Hors phase BETTING");

        Integer idx = msg.getSeatIndex();
        if (idx == null) {
            for (var e : t.getSeats().entrySet()) {
                if (email.equals(e.getValue().getEmail())) { idx = e.getKey(); break; }
            }
            if (idx == null) throw new IllegalStateException("Aucun siège pour cet utilisateur");
        }

        Seat seat = t.getSeats().get(idx);
        if (seat == null || seat.getStatus()==SeatStatus.EMPTY || !email.equals(seat.getEmail()))
            throw new IllegalStateException("Pas ton siège");

        long amount = msg.getAmount();

        // ✅ 0 = annuler sa mise pendant BETTING
        if (amount == 0L) {
            seat.getHand().setBet(0L);
            return;
        }

        if (amount < 0) throw new IllegalArgumentException("Mise invalide");

        long min = t.getMinBet()!=null?t.getMinBet():0L, max = t.getMaxBet()!=null?t.getMaxBet():Long.MAX_VALUE;
        if (min>0 && amount<min) throw new IllegalArgumentException("Mise inférieure au minimum ("+min+")");
        if (max>0 && max!=Long.MAX_VALUE && amount>max) throw new IllegalArgumentException("Mise supérieure au maximum ("+max+")");

        // ⬅️ amount = TOTAL voulu (pas un delta)
        seat.getHand().setBet(amount);
    }

}
