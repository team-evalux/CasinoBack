package org.example.service.blackjack;

import org.example.dto.blackjack.BetMsg;
import org.example.model.blackjack.*;
import org.example.service.blackjack.registry.TableRegistry;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.example.service.blackjack.betting.BettingService;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BettingServiceTest {

    @Mock
    TableRegistry registry;

    @InjectMocks
    BettingService service;

    BjTable table;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        table = new BjTable(1L, 5, false, null);
        table.setPhase(TablePhase.BETTING);
        table.setMinBet(10L);
        table.setMaxBet(200L);

        // joueur sur siège 0
        Seat s = table.getSeats().get(0);
        s.setStatus(SeatStatus.SEATED);
        s.setEmail("test@test.com");

        when(registry.get(1L)).thenReturn(table);
    }

    // ---------------------------------------------------------
    // 1) Mise normale
    // ---------------------------------------------------------
    @Test
    void bet_valide_modifieLaMise() {
        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setAmount(50);

        service.bet("test@test.com", msg);

        assertThat(table.getSeats().get(0).getHand().getBet()).isEqualTo(50);
    }

    // ---------------------------------------------------------
    // 2) Annulation de mise (amount = 0)
    // ---------------------------------------------------------
    @Test
    void bet_zero_annuleLaMise() {
        // Mise préalable
        table.getSeats().get(0).getHand().setBet(100);

        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setAmount(0);

        service.bet("test@test.com", msg);

        assertThat(table.getSeats().get(0).getHand().getBet()).isEqualTo(0);
    }

    // ---------------------------------------------------------
    // 3) Phase incorrecte
    // ---------------------------------------------------------
    @Test
    void bet_horsPhaseBetting_exception() {
        table.setPhase(TablePhase.PLAYING);

        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setAmount(50);

        assertThatThrownBy(() -> service.bet("test@test.com", msg))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hors phase BETTING");
    }

    // ---------------------------------------------------------
    // 4) Siège inexistant
    // ---------------------------------------------------------
    @Test
    void bet_pasTonSiege_exception() {

        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(1); // siège vide
        msg.setAmount(50);

        assertThatThrownBy(() -> service.bet("test@test.com", msg))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pas ton siège");
    }

    // ---------------------------------------------------------
    // 5) Détection du siège automatiquement (seatIndex null)
    // ---------------------------------------------------------
    @Test
    void bet_sansSeatIndex_detecteAutomatiquement() {
        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(null);
        msg.setAmount(80);

        service.bet("test@test.com", msg);

        assertThat(table.getSeats().get(0).getHand().getBet()).isEqualTo(80);
    }

    // ---------------------------------------------------------
    // 6) Aucun siège trouvé
    // ---------------------------------------------------------
    @Test
    void bet_sansSeatIndex_maisPasDansLaTable_exception() {
        table.getSeats().get(0).setEmail("autre@test.com");

        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(null);
        msg.setAmount(30);

        assertThatThrownBy(() -> service.bet("test@test.com", msg))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Aucun siège pour cet utilisateur");
    }

    // ---------------------------------------------------------
    // 7) Mise trop basse
    // ---------------------------------------------------------
    @Test
    void bet_minimumNonRespecte_exception() {
        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setAmount(5);

        assertThatThrownBy(() -> service.bet("test@test.com", msg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mise inférieure au minimum");
    }

    // ---------------------------------------------------------
    // 8) Mise trop haute
    // ---------------------------------------------------------
    @Test
    void bet_maxDepasse_exception() {
        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setAmount(300);

        assertThatThrownBy(() -> service.bet("test@test.com", msg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mise supérieure au maximum");
    }

    // ---------------------------------------------------------
    // 9) Mise négative
    // ---------------------------------------------------------
    @Test
    void bet_negative_exception() {
        BetMsg msg = new BetMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setAmount(-10);

        assertThatThrownBy(() -> service.bet("test@test.com", msg))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mise invalide");
    }
}
