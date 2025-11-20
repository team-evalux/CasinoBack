package org.example.service.blackjack;

import org.example.model.blackjack.BjTable;
import org.example.model.blackjack.BjTableEntity;
import org.example.repo.BjTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.example.service.blackjack.registry.TableRegistry;


import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TableRegistryTest {

    @Mock
    BjTableRepository repo;

    @InjectMocks
    TableRegistry registry;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // --------------------------------------------------------------
    // loadFromDb()
    // --------------------------------------------------------------
    @Test
    void loadFromDb_chargeBienLesTables() {
        BjTableEntity e1 = new BjTableEntity();
        e1.setId(1L);
        e1.setMaxSeats(5);
        e1.setPrivate(false);
        e1.setCode("C1");
        e1.setName("Publique");
        e1.setMinBet(10L);
        e1.setMaxBet(500L);
        e1.setCreatorEmail("a@test.com");
        e1.setCreatedAt(Instant.now());

        BjTableEntity e2 = new BjTableEntity();
        e2.setId(2L);
        e2.setMaxSeats(4);
        e2.setPrivate(true);
        e2.setCode("SECRET");
        e2.setName("Privée");
        e2.setMinBet(20L);
        e2.setMaxBet(200L);
        e2.setCreatorEmail("b@test.com");
        e2.setCreatedAt(Instant.now());

        when(repo.findAll()).thenReturn(List.of(e1, e2));

        registry.loadFromDb();

        assertThat(registry.all()).hasSize(2);

        BjTable t1 = registry.get(1L);
        assertThat(t1.getName()).isEqualTo("Publique");
        assertThat(t1.isPrivate()).isFalse();

        BjTable t2 = registry.get(2L);
        assertThat(t2.getName()).isEqualTo("Privée");
        assertThat(t2.isPrivate()).isTrue();
    }

    // --------------------------------------------------------------
    // get()
    // --------------------------------------------------------------
    @Test
    void get_tableExistante_retourneTable() {
        BjTable t = new BjTable(10L, 5, false, "X");
        registry.put(t);

        BjTable r = registry.get(10L);

        assertThat(r).isSameAs(t);
    }

    @Test
    void get_tableInexistante_exception() {
        assertThatThrownBy(() -> registry.get(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table inconnue");
    }

    // --------------------------------------------------------------
    // createAndPersist()
    // --------------------------------------------------------------
    @Test
    void createAndPersist_creeEtStocke() {
        BjTableEntity ent = new BjTableEntity();
        ent.setId(5L);
        ent.setMaxSeats(6);
        ent.setPrivate(false);
        ent.setCode("ABC");
        ent.setName("Test");
        ent.setMinBet(10L);
        ent.setMaxBet(100L);
        ent.setCreatorEmail("x@test.com");
        ent.setCreatedAt(Instant.now());

        when(repo.save(any())).thenReturn(ent);

        BjTable t = registry.createAndPersist(ent);

        assertThat(t.getId()).isEqualTo(5L);
        assertThat(t.getName()).isEqualTo("Test");
        assertThat(t.getMaxSeats()).isEqualTo(6);
        assertThat(registry.get(5L)).isSameAs(t);
    }

    // --------------------------------------------------------------
    // deleteFromDb()
    // --------------------------------------------------------------
    @Test
    void deleteFromDb_appelleLeRepo() {
        registry.deleteFromDb(123L);

        verify(repo).deleteById(123L);
    }

    // --------------------------------------------------------------
    // listPublic()
    // --------------------------------------------------------------
    @Test
    void listPublic_retourneSeulementLesTablesPubliques() {
        BjTable t1 = new BjTable(1L, 5, false, "C1"); // public
        BjTable t2 = new BjTable(2L, 5, true, "C2");  // privé

        registry.put(t1);
        registry.put(t2);

        List<BjTable> list = registry.listPublic();

        assertThat(list)
                .hasSize(1)
                .containsExactly(t1)
                .doesNotContain(t2);
    }
}
