package org.example.model.blackjack;

import java.security.SecureRandom;
import java.util.*;

public class Shoe {
    private final Deque<Card> cards = new ArrayDeque<>();
    private final SecureRandom rnd = new SecureRandom();

    public Shoe(int decks) {
        List<Card> tmp = new ArrayList<>();
        for (int d=0; d<decks; d++) {
            for (Card.Suit s : Card.Suit.values()) {
                for (Card.Rank r : Card.Rank.values()) tmp.add(new Card(r, s));
            }
        }
        Collections.shuffle(tmp, rnd);
        cards.addAll(tmp);
    }

    public Card draw() {
        if (cards.size() < 20) reshuffle();
        Card c = cards.pollFirst();
        if (c == null) {
            // reconstituer un nouveau shoe si jamais vidé (fallback)
            refill();
            c = cards.pollFirst();
        }
        return c;
    }

    private void refill() {
        List<Card> tmp = new ArrayList<>();
        for (int d=0; d<6; d++) { // même nombre de decks que le ctor
            for (Card.Suit s : Card.Suit.values())
                for (Card.Rank r : Card.Rank.values()) tmp.add(new Card(r, s));
        }
        Collections.shuffle(tmp, rnd);
        cards.clear();
        cards.addAll(tmp);
    }

    private void reshuffle() {
        List<Card> tmp = new ArrayList<>(cards);
        Collections.shuffle(tmp, rnd);
        cards.clear();
        cards.addAll(tmp);
    }
}
