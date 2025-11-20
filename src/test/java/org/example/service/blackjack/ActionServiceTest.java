package org.example.service.blackjack;

import org.example.dto.blackjack.ActionMsg;
import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.example.service.blackjack.registry.TableRegistry;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.example.service.blackjack.action.ActionService;


import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActionServiceTest {

    @Mock
    TableRegistry registry;

    @Mock
    WalletService wallet;

    @Mock
    UtilisateurRepository users;

    @InjectMocks
    ActionService service;

    BjTable table;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        table = new BjTable(1L, 5, false, null);
        table.setPhase(TablePhase.PLAYING);

        Seat s = table.getSeats().get(0);
        s.setStatus(SeatStatus.SEATED);
        s.setEmail("test@test.com");
        s.getHand().setBet(100);

        table.setCurrentSeatIndex(0);

        // Mock shoe
        table.getShoe().draw(); // on consomme une carte pour que draw() fonctionne
        when(registry.get(1L)).thenReturn(table);
    }

    // -------------------------------------------------------------------------
    // HIT
    // -------------------------------------------------------------------------
    @Test
    void hit_ajouteUneCarte() {
        ActionMsg msg = new ActionMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setType(ActionMsg.Type.HIT);

        int avant = table.getSeats().get(0).getHand().getCards().size();

        service.apply("test@test.com", msg);

        assertThat(table.getSeats().get(0).getHand().getCards().size())
                .isGreaterThan(avant);
    }

    // -------------------------------------------------------------------------
    // STAND
    // -------------------------------------------------------------------------
    @Test
    void stand_metStandingTrue() {
        ActionMsg msg = new ActionMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setType(ActionMsg.Type.STAND);

        service.apply("test@test.com", msg);

        assertThat(table.getSeats().get(0).getHand().isStanding())
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // DOUBLE
    // -------------------------------------------------------------------------
    @Test
    void double_doubleLaMise_etTireUneCarte_etStand() {
        Utilisateur u = new Utilisateur();
        when(users.findByEmail("test@test.com")).thenReturn(Optional.of(u));

        ActionMsg msg = new ActionMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setType(ActionMsg.Type.DOUBLE);

        int avant = table.getSeats().get(0).getHand().getCards().size();

        service.apply("test@test.com", msg);

        // Wallet a été débité
        verify(wallet).debiter(u, 100);

        // Mise doublée
        assertThat(table.getSeats().get(0).getHand().getBet()).isEqualTo(200);

        // Une carte en plus
        assertThat(table.getSeats().get(0).getHand().getCards().size())
                .isGreaterThan(avant);

        // Doit être standing
        assertThat(table.getSeats().get(0).getHand().isStanding()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Mauvaise phase
    // -------------------------------------------------------------------------
    @Test
    void pasEnPhasePlaying_exception() {
        table.setPhase(TablePhase.BETTING);

        ActionMsg msg = new ActionMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setType(ActionMsg.Type.HIT);

        assertThatThrownBy(() ->
                service.apply("test@test.com", msg)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hors phase PLAYING");
    }

    // -------------------------------------------------------------------------
    // Pas le tour du joueur
    // -------------------------------------------------------------------------
    @Test
    void pasTonTour_exception() {
        table.setCurrentSeatIndex(1); // pas le joueur 0

        ActionMsg msg = new ActionMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setType(ActionMsg.Type.HIT);

        assertThatThrownBy(() ->
                service.apply("test@test.com", msg)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pas ton tour");
    }

    // -------------------------------------------------------------------------
    // Pas ton siège
    // -------------------------------------------------------------------------
    @Test
    void pasTonSiege_exception() {
        table.getSeats().get(0).setEmail("autre@test.com");

        ActionMsg msg = new ActionMsg();
        msg.setTableId(1L);
        msg.setSeatIndex(0);
        msg.setType(ActionMsg.Type.HIT);

        assertThatThrownBy(() ->
                service.apply("test@test.com", msg)
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Pas ton siège");
    }
}
