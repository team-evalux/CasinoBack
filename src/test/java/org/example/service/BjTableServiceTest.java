package org.example.service;

import org.example.dto.blackjack.*;
import org.example.model.blackjack.*;
import org.example.service.blackjack.access.AccessService;
import org.example.service.blackjack.action.ActionService;
import org.example.service.blackjack.betting.BettingService;
import org.example.service.blackjack.engine.RoundEngine;
import org.example.service.blackjack.entry.EntryService;
import org.example.service.blackjack.registry.TableRegistry;
import org.example.service.blackjack.util.Locks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BjTableServiceTest {

    @Mock TableRegistry registry;
    @Mock AccessService access;
    @Mock EntryService entry;
    @Mock BettingService betting;
    @Mock RoundEngine engine;
    @Mock ActionService actions;
    @Mock
    SimpMessagingTemplate broker;


    @InjectMocks BjTableService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    private BjTable table(Long id) {
        BjTable t = new BjTable(id, 5, false, null);
        t.setCreatorEmail("creator@test.com");
        return t;
    }

    // ------------------------------------------------------------
    // CREATE TABLE
    // ------------------------------------------------------------
    @Test
    void createTable_public_ok() {
        BjTable t = table(1L);

        when(access.createTable(any(), anyBoolean(), any(), anyInt(), any(), anyLong(), anyLong()))
                .thenReturn(t);

        BjTable out = service.createTable("aaa@test.com", false, null, 5,
                "Table A", 10, 100);

        assertThat(out).isNotNull();
        verify(engine).startBetting(t);
    }

    // ------------------------------------------------------------
    // JOIN OR CREATE
    // ------------------------------------------------------------
    @Test
    void joinOrCreate_create_public() {
        BjTable t = table(1L);

        when(access.createTable(any(), eq(false), any(), anyInt(), any(), anyLong(), anyLong()))
                .thenReturn(t);
        when(entry.enter(anyString(), anyLong(), any())).thenReturn(t);

        JoinOrCreateMsg msg = new JoinOrCreateMsg();
        msg.setCreatePublic(true);
        msg.setName("Test");

        BjTable out = service.joinOrCreate("x@test.com", msg);
        assertThat(out.getId()).isEqualTo(1L);

        verify(access).createTable(eq("x@test.com"), eq(false), any(), anyInt(), eq("Test"), anyLong(), anyLong());
        verify(engine).startBetting(t);
    }

    @Test
    void joinOrCreate_existing_table() {
        BjTable t = table(2L);
        when(entry.enter(anyString(), eq(2L), any())).thenReturn(t);

        JoinOrCreateMsg msg = new JoinOrCreateMsg();
        msg.setTableId(2L);

        BjTable out = service.joinOrCreate("p@test.com", msg);

        assertThat(out.getId()).isEqualTo(2L);
        verify(entry).enter("p@test.com", 2L, null);
    }

    // ------------------------------------------------------------
    // CLOSE TABLE
    // ------------------------------------------------------------
    @Test
    void closeTable_betting_phase_close_immediate() {
        BjTable t = table(3L);
        t.setPhase(TablePhase.BETTING);

        when(registry.get(3L)).thenReturn(t);

        service.closeTable(3L, "creator@test.com");

        verify(entry).onTableClosed(t);
        verify(registry).remove(3L);
        verify(registry).deleteFromDb(3L);
        verify(access).onTableClosed(t, "creator@test.com");
    }

    @Test
    void closeTable_during_playing_sets_pending() {
        BjTable t = table(4L);
        t.setPhase(TablePhase.PLAYING);

        when(registry.get(4L)).thenReturn(t);

        service.closeTable(4L, "creator@test.com");

        assertThat(t.isPendingClose()).isTrue();
        verify(entry, never()).onTableClosed(any());
    }

    // ------------------------------------------------------------
    // BET
    // ------------------------------------------------------------
    @Test
    void bet_calls_betting_and_broadcast() {
        BjTable t = table(5L);
        when(registry.get(5L)).thenReturn(t);

        BetMsg msg = new BetMsg();
        msg.setTableId(5L);
        msg.setAmount(50L);

        service.bet("u@test.com", msg);

        verify(betting).bet("u@test.com", msg);
        verify(engine).broadcastState(t);
    }

    // ------------------------------------------------------------
    // ACTION
    // ------------------------------------------------------------
    @Test
    void action_calls_actionService_and_advances_engine() {
        BjTable t = table(6L);
        when(registry.get(6L)).thenReturn(t);

        ActionMsg msg = new ActionMsg();
        msg.setTableId(6L);
        msg.setSeatIndex(0);
        msg.setType(ActionMsg.Type.HIT);

        service.action("u@test.com", msg);

        verify(actions).apply("u@test.com", msg);
        verify(engine).onPlayerAction(t);
    }

}
