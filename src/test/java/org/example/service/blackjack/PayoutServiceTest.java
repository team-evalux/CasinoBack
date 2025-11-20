package org.example.service.blackjack;

import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.example.service.blackjack.engine.PayoutService;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PayoutServiceTest {

    @Mock WalletService wallet;
    @Mock UtilisateurRepository users;

    @InjectMocks
    PayoutService service;

    BjTable table;
    PlayerState dealer;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        table = new BjTable(1L, 5, false, "T1");
        dealer = table.getDealer();
        dealer.getCards().clear();
    }

    // --------------------------------------------------------------
    // TEST 1 : le joueur gagne
    // --------------------------------------------------------------
    @Test
    void computeAndPay_joueurGagne() {

        // joueur placé
        Seat seat = table.getSeats().get(0);
        seat.setStatus(SeatStatus.SEATED);
        seat.setEmail("p1@test.com");
        seat.getHand().setBet(100);

        // mock user en DB
        var mockUser = new org.example.model.Utilisateur();
        when(users.findByEmail("p1@test.com")).thenReturn(Optional.of(mockUser));

        // cartes (21 joueur, 18 dealer)
        seat.getHand().getCards().add(new Card(Card.Rank.TEN, Card.Suit.CLUBS));
        seat.getHand().getCards().add(new Card(Card.Rank.ACE, Card.Suit.HEARTS));

        dealer.getCards().add(new Card(Card.Rank.TEN, Card.Suit.CLUBS));
        dealer.getCards().add(new Card(Card.Rank.EIGHT, Card.Suit.DIAMONDS));

        // Act
        var payouts = service.computeAndPay(table);

        // Assert
        assertThat(payouts).hasSize(1);
        assertThat((String) payouts.get(0).get("outcome")).isEqualTo("WIN");

        // Vérifie crédit du joueur
        verify(wallet).crediter(mockUser, 200); // 100 bet → 200 gagné
    }


    // --------------------------------------------------------------
    // TEST 2 : le joueur perd
    // --------------------------------------------------------------
    @Test
    void computeAndPay_joueurPerd() {

        Seat seat = table.getSeats().get(0);
        seat.setStatus(SeatStatus.SEATED);
        seat.setEmail("p1@test.com");
        seat.getHand().setBet(50);

        var mockUser = new org.example.model.Utilisateur();
        when(users.findByEmail("p1@test.com")).thenReturn(Optional.of(mockUser));

        // Joueur = 23 -> BUSTED
        seat.getHand().add(new Card(Card.Rank.KING, Card.Suit.CLUBS));
        seat.getHand().add(new Card(Card.Rank.QUEEN, Card.Suit.CLUBS));
        seat.getHand().add(new Card(Card.Rank.THREE, Card.Suit.CLUBS));

        // Dealer = 11
        dealer.add(new Card(Card.Rank.FIVE, Card.Suit.CLUBS));
        dealer.add(new Card(Card.Rank.SIX, Card.Suit.CLUBS));

        // Act
        var payouts = service.computeAndPay(table);

        // Assert
        assertThat(payouts).hasSize(1);
        assertThat((String) payouts.get(0).get("outcome")).isEqualTo("LOSE");

        verify(wallet, never()).crediter(any(), anyLong());
    }



    // --------------------------------------------------------------
    // TEST 3 : Blackjack naturel
    // --------------------------------------------------------------
    @Test
    void computeAndPay_blackjack() {

        Seat seat = table.getSeats().get(0);
        seat.setStatus(SeatStatus.SEATED);
        seat.setEmail("bj@test.com");
        seat.getHand().setBet(100);

        var mockUser = new org.example.model.Utilisateur();
        when(users.findByEmail("bj@test.com")).thenReturn(Optional.of(mockUser));

        // Joueur blackjack
        seat.getHand().getCards().add(new Card(Card.Rank.ACE, Card.Suit.HEARTS));
        seat.getHand().getCards().add(new Card(Card.Rank.KING, Card.Suit.SPADES));
        seat.getHand().setBlackjack(true);



        // Dealer pas BJ
        dealer.getCards().add(new Card(Card.Rank.NINE, Card.Suit.CLUBS));
        dealer.getCards().add(new Card(Card.Rank.EIGHT, Card.Suit.CLUBS));



        // Act
        var payouts = service.computeAndPay(table);

        // Assert
        assertThat((String) payouts.get(0).get("outcome")).isEqualTo("BLACKJACK");

        verify(wallet).crediter(mockUser, 250); // 100 bet → 250 payé (3:2)
    }


    // --------------------------------------------------------------
    // TEST 4 : PUSH
    // --------------------------------------------------------------
    @Test
    void computeAndPay_push() {

        Seat seat = table.getSeats().get(0);
        seat.setStatus(SeatStatus.SEATED);
        seat.setEmail("push@test.com");
        seat.getHand().setBet(80);

        var mockUser = new org.example.model.Utilisateur();
        when(users.findByEmail("push@test.com")).thenReturn(Optional.of(mockUser));

        // joueur = 20
        seat.getHand().getCards().add(new Card(Card.Rank.KING, Card.Suit.CLUBS));
        seat.getHand().getCards().add(new Card(Card.Rank.QUEEN, Card.Suit.DIAMONDS));

        // dealer = 20
        dealer.getCards().add(new Card(Card.Rank.TEN, Card.Suit.SPADES));
        dealer.getCards().add(new Card(Card.Rank.JACK, Card.Suit.SPADES));

        // Act
        var payouts = service.computeAndPay(table);

        // Assert
        assertThat((String) payouts.get(0).get("outcome")).isEqualTo("PUSH");

        verify(wallet).crediter(mockUser, 80); // pari rendu
    }
}
