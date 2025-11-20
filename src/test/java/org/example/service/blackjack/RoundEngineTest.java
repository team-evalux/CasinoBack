package org.example.service.blackjack;

import org.example.model.blackjack.*;
import org.example.model.blackjack.rules.DealingRules;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.example.service.blackjack.access.AccessService;
import org.example.service.blackjack.engine.PayoutService;
import org.example.service.blackjack.engine.RoundEngine;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Locks;
import org.example.service.blackjack.util.Payloads;
import org.example.service.blackjack.util.Timeouts;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.Map;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RoundEngineTest {

    @Mock TableRegistry registry;
    @Mock Payloads payloads;
    @Mock
    PayoutService payouts;
    @Mock WalletService wallet;
    @Mock UtilisateurRepository users;
    @Mock Timeouts timeouts;
    @Mock Locks locks;
    @Mock AccessService access;

    @InjectMocks
    RoundEngine engine;

    BjTable table;

    AutoCloseable staticMock;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        table = new BjTable(1L, 5, false, null);
        table.getSeats().get(0).setStatus(SeatStatus.SEATED);
        table.getSeats().get(0).getHand().setBet(100);
        when(locks.of(anyLong())).thenReturn(new Object());

        // Mock du payload
        when(payloads.tableState(any(), anyLong()))
                .thenReturn(Map.of("dummy", "ok"));

        // Mock DealingRules
        staticMock = mockStatic(DealingRules.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        staticMock.close();
    }

    // -------------------------------------------------------------------------
    // startBetting()
    // -------------------------------------------------------------------------
    @Test
    void startBetting_initialisePhaseEtLanceTimer() {
        // Act
        engine.startBetting(table);

        // Assert
        assertThat(table.getPhase()).isEqualTo(TablePhase.BETTING);
        verify(access).broadcastToTable(eq(table), eq("TABLE_STATE"), any());
        verify(timeouts).schedule(
                eq(table.getId()),
                eq("betting"),
                anyLong(),
                any(Runnable.class)
        );
    }

    // -------------------------------------------------------------------------
    // lockBetsAndDeal()
    // -------------------------------------------------------------------------


    @Test
    void lockBetsAndDeal_redemarreBetting_siPersonneABete() {
        // Annuler la mise
        table.getSeats().get(0).getHand().setBet(0);

        // Act
        engine.lockBetsAndDeal(table);

        // => startBetting doit être appelé à nouveau
        assertThat(table.getPhase()).isEqualTo(TablePhase.BETTING);
    }

    // -------------------------------------------------------------------------
    // nextTurnOrDealer()
    // -------------------------------------------------------------------------
    @Test
    void nextTurnOrDealer_changeVersProchainJoueurActif() {
        // joueur 0 = a bet, joueur 1 = actif
        table.getSeats().get(1).setStatus(SeatStatus.SEATED);
        table.getSeats().get(1).getHand().setBet(50);

        table.setCurrentSeatIndex(0);
        table.setPhase(TablePhase.PLAYING);

        engine.nextTurnOrDealer(table);

        assertThat(table.getCurrentSeatIndex()).isEqualTo(1);
        verify(timeouts).schedule(eq(1L), contains("turn-"), anyLong(), any());
    }

    @Test
    void nextTurnOrDealer_tousTermines_appelleDealerTurn() {
        table.setCurrentSeatIndex(0);
        table.setPhase(TablePhase.PLAYING);

        table.getSeats().get(0).getHand().setStanding(true);

        engine.nextTurnOrDealer(table);

        assertThat(table.getPhase()).isEqualTo(TablePhase.DEALER_TURN);
    }

    // -------------------------------------------------------------------------
    // dealerTurn()
    // -------------------------------------------------------------------------
    @Test
    void dealerTurn_changePhaseEtEnvoieEvent() {
        engine.dealerTurn(table);

        assertThat(table.getPhase()).isEqualTo(TablePhase.DEALER_TURN);

        verify(access).broadcastToTable(eq(table), eq("DEALER_TURN_START"), any());

        // Le timeout du dealer doit être programmé
        verify(timeouts).schedule(
                eq(table.getId()),
                eq("dealerDraw"),
                anyLong(),
                any(Runnable.class)
        );
    }

    // -------------------------------------------------------------------------
    // onPlayerAction()
    // -------------------------------------------------------------------------
    @Test
    void onPlayerAction_avanceAuJoueurSuivant() {

        table.setPhase(TablePhase.PLAYING);
        table.setCurrentSeatIndex(0);

        // Le joueur courant s’arrête => nextTurnOrDealer doit passer à Dealer
        table.getSeats().get(0).getHand().setStanding(true);

        engine.onPlayerAction(table);

        // Après seul joueur => dealerTurn
        assertThat(table.getPhase()).isEqualTo(TablePhase.DEALER_TURN);
    }

    // -------------------------------------------------------------------------
    // broadcastState()
    // -------------------------------------------------------------------------
    @Test
    void broadcastState_envoieEvent() {
        engine.broadcastState(table);

        verify(access).broadcastToTable(eq(table), eq("TABLE_STATE"), any());
    }
}

