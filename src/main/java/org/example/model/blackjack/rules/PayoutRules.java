package org.example.model.blackjack.rules;

import org.example.model.blackjack.PlayerState;

public final class PayoutRules {
    private PayoutRules(){}

    public record Outcome(long credit, String outcome) {}

    public static Outcome compute(PlayerState player, PlayerState dealer, long bet) {
        if (bet <= 0) return new Outcome(0, "NO_BET");
        boolean pBJ = player.isBlackjack(), dBJ = dealer.isBlackjack();
        int pt = player.bestTotal(), dt = dealer.bestTotal();
        if (player.isBusted()) return new Outcome(0, "LOSE");
        if (pBJ && dBJ) return new Outcome(bet, "PUSH");
        if (pBJ) return new Outcome(bet + (bet * 3) / 2, "BLACKJACK");
        if (dBJ) return new Outcome(0, "LOSE");
        if (dt > 21 || pt > dt) return new Outcome(bet * 2, "WIN");
        if (pt == dt) return new Outcome(bet, "PUSH");
        return new Outcome(0, "LOSE");
    }
}
