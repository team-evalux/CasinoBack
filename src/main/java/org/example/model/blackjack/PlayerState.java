package org.example.model.blackjack;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PlayerState {
    private final List<Card> cards = new ArrayList<>();
    private boolean standing = false;
    private boolean busted = false;
    private boolean blackjack = false;
    private long bet = 0;

    public int bestTotal() {
        int sum = 0, aces = 0;
        for (Card c : cards) {
            sum += c.value();
            if (c.getRank() == Card.Rank.ACE) aces++;
        }
        while (sum > 21 && aces > 0) { sum -= 10; aces--; }
        return sum;
    }

    public void add(Card c) {
        cards.add(c);
        int total = bestTotal();
        if (cards.size() == 2 && total == 21) blackjack = true;
        if (total > 21) busted = true;
    }
}
