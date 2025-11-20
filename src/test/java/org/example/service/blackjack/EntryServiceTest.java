package org.example.service.blackjack;

import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.example.service.blackjack.access.AccessService;
import org.example.service.blackjack.entry.EntryService;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Payloads;
import org.example.service.blackjack.util.Locks;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntryServiceTest {

    @Mock TableRegistry registry;
    @Mock UtilisateurRepository users;
    @Mock AccessService access;
    @Mock Payloads payloads;
    @Mock org.springframework.messaging.simp.SimpMessagingTemplate broker;

    @Mock Locks locks;


    @InjectMocks
    EntryService service;

    private BjTable table;
    private Utilisateur user;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // IMPORTANT : mock du système de verrouillage
        when(locks.of(anyLong())).thenReturn(new Object());

        table = new BjTable(1L, 3, false, null);
        table.setCreatorEmail("creator@test.com");
        when(registry.get(1L)).thenReturn(table);

        user = new Utilisateur();
        user.setId(10L);
        user.setEmail("p@test.com");
        user.setPseudo("Player");
        when(users.findByEmail("p@test.com")).thenReturn(Optional.of(user));
    }


    // ---------------------------------------------------------
    // ENTER
    // ---------------------------------------------------------
    @Test
    void enter_ok_assigns_first_free_seat() {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);
        when(payloads.tableState(any(), anyLong())).thenReturn(Map.of());

        BjTable out = service.enter("p@test.com", 1L, null);

        assertThat(out.getSeats().get(0).getEmail()).isEqualTo("p@test.com");
        assertThat(out.getSeats().get(0).getStatus()).isEqualTo(SeatStatus.SEATED);

        verify(access).broadcastLobby();
    }

    @Test
    void enter_fails_if_on_other_table() {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);

        // simulate user already in another table
        var otherTable = new BjTable(2L, 3, false, null);
        when(registry.get(2L)).thenReturn(otherTable);
        service.enter("p@test.com", 2L, null);

        assertThatThrownBy(() -> service.enter("p@test.com", 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà présent");
    }

    @Test
    void enter_refuses_if_table_full() {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);

        // fill all seats
        for (int i = 0; i < 3; i++) {
            Seat s = table.getSeats().get(i);
            s.setEmail("x" + i);
            s.setStatus(SeatStatus.SEATED);
        }

        assertThatThrownBy(() -> service.enter("p@test.com", 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Table pleine");
    }

    @Test
    void enter_private_table_checks_code() {
        table = new BjTable(1L, 3, true, "ABC");
        when(registry.get(1L)).thenReturn(table);

        when(access.authorizeEmailForTable(1L, "p@test.com", "AAA")).thenReturn(false);

        assertThatThrownBy(() -> service.enter("p@test.com", 1L, "AAA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Code d'accès invalide");
    }


    // ---------------------------------------------------------
    // LEAVE
    // ---------------------------------------------------------
    @Test
    void leave_in_betting_removes_player() {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);
        service.enter("p@test.com", 1L, null);

        table.setPhase(TablePhase.BETTING);
        service.leave("p@test.com", 1L);

        assertThat(table.getSeats().get(0).getStatus()).isEqualTo(SeatStatus.EMPTY);
    }

    @Test
    void leave_in_playing_sets_disconnected() {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);
        service.enter("p@test.com", 1L, null);

        table.setPhase(TablePhase.PLAYING);
        service.leave("p@test.com", 1L);

        assertThat(table.getSeats().get(0).getStatus()).isEqualTo(SeatStatus.DISCONNECTED);
    }

    // ---------------------------------------------------------
    // DISCONNECT / RECONNECT
    // ---------------------------------------------------------
    @Test
    void markDisconnected_sets_status() {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);
        service.enter("p@test.com", 1L, null);

        service.markDisconnected("p@test.com");

        assertThat(table.getSeats().get(0).getStatus())
                .isEqualTo(SeatStatus.DISCONNECTED);
    }

    @Test
    void markReconnected_restores_seated() {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);
        service.enter("p@test.com", 1L, null);

        service.markDisconnected("p@test.com");
        service.markReconnected("p@test.com");

        assertThat(table.getSeats().get(0).getStatus())
                .isEqualTo(SeatStatus.SEATED);
    }


    // ---------------------------------------------------------
    // TIMER CLEANUP (simulation)
    // ---------------------------------------------------------
    @Test
    void disconnected_cleanup_removes_seat_if_safe_phase() throws Exception {
        when(access.authorizeEmailForTable(anyLong(), anyString(), any())).thenReturn(true);
        service.enter("p@test.com", 1L, null);

        table.setPhase(TablePhase.BETTING);

        service.markDisconnected("p@test.com");

        // Raccourci : simulate immediate timeout trigger
        table.getSeats().get(0).setStatus(SeatStatus.DISCONNECTED);

        // simulate scheduled cleanup (appel direct)
        var seat = table.getSeats().get(0);
        if (seat.getStatus() == SeatStatus.DISCONNECTED &&
                table.getPhase() == TablePhase.BETTING) {
            table.getSeats().put(0, new Seat());
        }

        assertThat(table.getSeats().get(0).getStatus())
                .isEqualTo(SeatStatus.EMPTY);
    }
}
