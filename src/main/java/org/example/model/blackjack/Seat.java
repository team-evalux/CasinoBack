package org.example.model.blackjack;

import lombok.Data;

@Data
public class Seat {
    private Long userId;         // id utilisateur
    private String email;        // email (identité publique de la table)
    private SeatStatus status = SeatStatus.EMPTY;
    private PlayerState hand = new PlayerState();

    public boolean isActivePlayer() {
        return status != SeatStatus.EMPTY
                && !hand.isBusted()   // ← au lieu de hand.busted()
                && !hand.isStanding();
    }


    public void resetForNextHand() {
        hand = new PlayerState();
        if (status == SeatStatus.DISCONNECTED) {
            // on laisse le siège pour un laps de temps, mais sans mise => ne joue pas
        }
    }
}
