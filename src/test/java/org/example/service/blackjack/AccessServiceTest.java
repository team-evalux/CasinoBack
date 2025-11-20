package org.example.service.blackjack;

import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.UtilisateurRepository;
import org.example.service.blackjack.access.AccessService;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Payloads;

import org.junit.jupiter.api.*;
import org.mockito.*;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessServiceTest {

    @Mock TableRegistry registry;
    @Mock UtilisateurRepository users;
    @Mock SimpMessagingTemplate broker;
    @Mock Payloads payloads;

    @InjectMocks
    AccessService access;

    BjTable tablePublic;
    BjTable tablePrivate;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // ------- TABLE PUBLIQUE -------
        tablePublic = new BjTable(1L, 5, false, "CODE-PUB");
        tablePublic.setName("Pub");
        tablePublic.setCreatorEmail("owner@test.com");
        tablePublic.setMinBet(10L);
        tablePublic.setMaxBet(500L);

        // ------- TABLE PRIVÉE -------
        tablePrivate = new BjTable(2L, 5, true, "SECRET");
        tablePrivate.setName("Privée");
        tablePrivate.setCreatorEmail("owner@test.com");
        tablePrivate.setMinBet(10L);
        tablePrivate.setMaxBet(500L);

        when(registry.get(1L)).thenReturn(tablePublic);
        when(registry.get(2L)).thenReturn(tablePrivate);
        when(registry.all()).thenReturn(List.of(tablePublic, tablePrivate));
    }

    // ------------------------------------------------------
    // createTable()
    // ------------------------------------------------------
    @Test
    void createTable_public_ok() {
        var mockUser = new Utilisateur(1L, "user@test.com", "User", "hash", null, true, null, "USER");
        when(users.findByEmail("user@test.com")).thenReturn(Optional.of(mockUser));

        when(registry.createAndPersist(any()))
                .thenAnswer(invocation -> {
                    BjTableEntity e = invocation.getArgument(0);
                    return new BjTable(99L, e.getMaxSeats(), e.isPrivate(), e.getCode());
                });

        var t = access.createTable("user@test.com", false, null, 5,
                "MaTable", 10, 500);

        assertThat(t.getId()).isEqualTo(99L);
        assertThat(t.isPrivate()).isFalse();
        verify(broker).convertAndSend(eq("/topic/bj/lobby"), any(Object.class));
    }

    @Test
    void createTable_prive_sansCode_exception() {
        assertThatThrownBy(() ->
                access.createTable("x@test.com", true, null, 5, "Privée", 10, 500)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createTable_prive_ok_et_emailAutorise() {
        when(users.findByEmail("owner@test.com"))
                .thenReturn(Optional.of(new Utilisateur(10L,"owner@test.com","Owner","h",null,true,null,"USER")));

        when(registry.createAndPersist(any()))
                .thenAnswer(invocation -> {
                    BjTableEntity e = invocation.getArgument(0);
                    return new BjTable(77L, e.getMaxSeats(), true, e.getCode());
                });

        var t = access.createTable("owner@test.com", true, "CODEX", 5, "Privée", 10, 500);

        assertThat(t.isPrivate()).isTrue();
        assertThat(access.allowedFor(t)).contains("owner@test.com");
        verify(broker).convertAndSend(eq("/topic/bj/lobby"), any(Object.class));
    }

    // ------------------------------------------------------
    // authorizeEmailForTable()
    // ------------------------------------------------------
    @Test
    void authorizeEmailForTable_public_ok() {
        boolean ok = access.authorizeEmailForTable(1L, "x@test.com", null);
        assertThat(ok).isTrue();
    }

    @Test
    void authorizeEmailForTable_prive_codeOK() {
        boolean ok = access.authorizeEmailForTable(2L, "user@test.com", "SECRET");
        assertThat(ok).isTrue();
        assertThat(access.allowedFor(tablePrivate)).contains("user@test.com");
    }

    @Test
    void authorizeEmailForTable_prive_codeKO() {
        boolean ok = access.authorizeEmailForTable(2L, "hacker@test.com", "WRONG");
        assertThat(ok).isFalse();
    }

    // ------------------------------------------------------
    // allowedFor()
    // ------------------------------------------------------
    @Test
    void allowedFor_inclutCreateurEtJoueurs() {
        tablePublic.getSeats().get(0).setEmail("p1@test.com");

        var allowed = access.allowedFor(tablePublic);

        assertThat(allowed)
                .contains("owner@test.com")
                .contains("p1@test.com");
    }

    // ------------------------------------------------------
    // broadcastToTable()
    // ------------------------------------------------------
    @Test
    void broadcast_public() {
        access.broadcastToTable(tablePublic, "TEST", Map.of("x",1));

        verify(broker).convertAndSend(eq("/topic/bj/table/1"), any(Object.class));

    }

    @Test
    void broadcast_prive() {
        tablePrivate.getSeats().get(0).setEmail("p1@test.com");

        access.broadcastToTable(tablePrivate, "TEST", Map.of("x",1));

        verify(broker).convertAndSendToUser(eq("owner@test.com"),
                eq("/queue/bj/table/2"), any());
        verify(broker).convertAndSendToUser(eq("p1@test.com"),
                eq("/queue/bj/table/2"), any());
    }

    // ------------------------------------------------------
    // onTableClosed()
    // ------------------------------------------------------
    @Test
    void onTableClosed_public() {
        access.onTableClosed(tablePublic, "owner@test.com");

        verify(broker).convertAndSend(eq("/topic/bj/table/1"), any(Object.class));
        verify(broker).convertAndSend(eq("/topic/bj/lobby"), any(Object.class));
    }

    @Test
    void onTableClosed_prive() {
        tablePrivate.getSeats().get(0).setEmail("p1@test.com");

        access.onTableClosed(tablePrivate, "owner@test.com");

        verify(broker).convertAndSendToUser(eq("owner@test.com"), eq("/queue/bj/table/2"), any());
        verify(broker).convertAndSendToUser(eq("p1@test.com"), eq("/queue/bj/table/2"), any());
        verify(broker).convertAndSend(eq("/topic/bj/lobby"), any(Object.class));
    }

    // ------------------------------------------------------
    // broadcastLobby()
    // ------------------------------------------------------
    @Test
    void broadcastLobby_ok() {
        access.broadcastLobby();
        verify(broker).convertAndSend(eq("/topic/bj/lobby"), any(Object.class));
    }
}
