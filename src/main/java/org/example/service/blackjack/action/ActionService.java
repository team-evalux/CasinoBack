package org.example.service.blackjack.action;

import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.ActionMsg;
import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.example.service.blackjack.registry.TableRegistry;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActionService {
    private final TableRegistry registry;
    private final WalletService wallet;
    private final UtilisateurRepository users;

    public void apply(String email, ActionMsg msg) {
        BjTable t = registry.get(msg.getTableId());
        if (t.getPhase() != TablePhase.PLAYING) throw new IllegalStateException("Hors phase PLAYING");
        if (!msg.getSeatIndex().equals(t.getCurrentSeatIndex())) throw new IllegalStateException("Pas ton tour");

        Seat seat = t.getSeats().get(msg.getSeatIndex());
        if (seat == null || seat.getStatus()==SeatStatus.EMPTY || !email.equals(seat.getEmail()))
            throw new IllegalStateException("Pas ton siège");

        switch (msg.getType()) {
            case HIT -> {
                seat.getHand().add(t.getShoe().draw());
            }
            case STAND -> seat.getHand().setStanding(true);
            case DOUBLE -> {
                long add = seat.getHand().getBet();
                Utilisateur u = users.findByEmail(email).orElseThrow();
                wallet.debiter(u, add);
                seat.getHand().setBet(seat.getHand().getBet() + add);
                seat.getHand().add(t.getShoe().draw());
                seat.getHand().setStanding(true);
            }
            default -> throw new IllegalArgumentException("Action non supportée");
        }
    }
}
