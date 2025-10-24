package org.example.model.blackjack.rules;

import org.example.model.blackjack.Card;
import org.example.model.blackjack.PlayerState;

public final class HandRules {
    private HandRules(){}

    public static int value(Card c) {
        return switch (c.getRank()) {
            case TWO -> 2; case THREE -> 3; case FOUR -> 4; case FIVE -> 5; case SIX -> 6;
            case SEVEN -> 7; case EIGHT -> 8; case NINE -> 9; case TEN, JACK, QUEEN, KING -> 10;
            case ACE -> 11;
        };
    }

    public static int bestTotal(Iterable<Card> cards) {
        int sum = 0, aces = 0;
        for (Card c : cards) {
            sum += value(c);
            if (c.getRank() == Card.Rank.ACE) aces++;
        }
        while (sum > 21 && aces-- > 0) sum -= 10;
        return sum;
    }

    public static void addCard(PlayerState p, Card c) {
        p.getCards().add(c);
        int total = bestTotal(p.getCards());
        if (p.getCards().size() == 2 && total == 21) p.setBlackjack(true);
        if (total > 21) p.setBusted(true);
    }
}
