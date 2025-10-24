package org.example.model.blackjack.rules;

import org.example.model.blackjack.BjTable;
import org.example.model.blackjack.Seat;

public final class DealingRules {
    private DealingRules(){}

    public static void dealInitial(BjTable t) {
        // clear player hands
        for (Seat s : t.getSeats().values()) {
            s.getHand().getCards().clear();
            s.getHand().setStanding(false);
            s.getHand().setBusted(false);
            s.getHand().setBlackjack(false);
        }
        // clear dealer
        t.getDealer().getCards().clear();
        t.getDealer().setStanding(false);
        t.getDealer().setBusted(false);
        t.getDealer().setBlackjack(false);

        for (int i = 0; i < 2; i++) {
            for (Seat s : t.getSeats().values()) {
                if (s.getHand().getBet() > 0)
                    HandRules.addCard(s.getHand(), t.getShoe().draw());
            }
            HandRules.addCard(t.getDealer(), t.getShoe().draw());
        }
    }
}
