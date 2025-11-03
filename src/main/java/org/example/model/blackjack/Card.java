package org.example.model.blackjack;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Card {
    private final Rank rank;
    private final Suit suit;

    public int value() {
        return switch (rank) {
            case TWO -> 2; case THREE -> 3; case FOUR -> 4; case FIVE -> 5; case SIX -> 6;
            case SEVEN -> 7; case EIGHT -> 8; case NINE -> 9; case TEN, JACK, QUEEN, KING -> 10;
            case ACE -> 11; // géré ensuite (A vaut 1 si nécessaire)
        };
    }

    public enum Suit { CLUBS, DIAMONDS, HEARTS, SPADES }
    public enum Rank { TWO,THREE,FOUR,FIVE,SIX,SEVEN,EIGHT,NINE,TEN,JACK,QUEEN,KING,ACE }
}
