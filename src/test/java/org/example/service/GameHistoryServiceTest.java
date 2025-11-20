package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.GameHistoryAggregate;
import org.example.model.Utilisateur;
import org.example.repo.GameHistoryAggregateRepository;
import org.example.repo.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameHistoryServiceTest {

    @Mock
    private GameHistoryAggregateRepository repo;

    @Mock
    private UtilisateurRepository utilisateurRepo;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GameHistoryService historyService;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        return u;
    }

    // --- record() ---

    @Test
    void record_shouldCreateAggregateAndSaveEntries_whenNoExistingAggregate() throws Exception {
        Utilisateur u = buildUser();
        String game = "mines";

        when(repo.findByUtilisateurIdAndGame(1L, game)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"dummy\":true}]");
        when(repo.save(any(GameHistoryAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        historyService.record(u, game, "WIN", 100L, 200L, 2);

        // On capture l'aggregate sauvegardé
        ArgumentCaptor<GameHistoryAggregate> agCaptor = ArgumentCaptor.forClass(GameHistoryAggregate.class);
        verify(repo).save(agCaptor.capture());
        GameHistoryAggregate savedAg = agCaptor.getValue();

        assertThat(savedAg.getUtilisateurId()).isEqualTo(1L);
        assertThat(savedAg.getGame()).isEqualTo("mines");
        assertThat(savedAg.getEntriesJson()).isEqualTo("[{\"dummy\":true}]");
        assertThat(savedAg.getUpdatedAt()).isNotNull();

        // On capture aussi la liste passée à ObjectMapper pour vérifier la nouvelle entrée
        ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(objectMapper).writeValueAsString(listCaptor.capture());
        @SuppressWarnings("unchecked")
        List<GameHistoryService.Entry> entries = listCaptor.getAllValues().get(0);

        assertThat(entries).hasSize(1);
        GameHistoryService.Entry e = entries.get(0);
        assertThat(e.outcome).isEqualTo("WIN");
        assertThat(e.montantJoue).isEqualTo(100L);
        assertThat(e.montantGagne).isEqualTo(200L);
        assertThat(e.multiplier).isEqualTo(2);
        assertThat(e.id).isEqualTo(e.createdAt); // dans ton code tu mets les deux à "now"
    }

    @Test
    void record_shouldKeepAtMost10Entries_andPrependNewest() throws Exception {
        Utilisateur u = buildUser();
        String game = "slot";

        when(repo.findByUtilisateurIdAndGame(1L, game)).thenReturn(Optional.empty());
        when(repo.save(any(GameHistoryAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // On garde la dernière liste passée à l'ObjectMapper
        AtomicReference<List<GameHistoryService.Entry>> lastListRef = new AtomicReference<>();
        when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<GameHistoryService.Entry> list = invocation.getArgument(0);
            lastListRef.set(list);
            return "json";
        });

        // On enregistre 15 parties pour le même user + game
        for (int i = 0; i < 15; i++) {
            historyService.record(u, game, "OUTCOME_" + i, 10L, 20L, 2);
        }

        List<GameHistoryService.Entry> lastList = lastListRef.get();
        assertThat(lastList).isNotNull();
        assertThat(lastList).hasSize(10); // MAX_ENTRIES

        // La dernière partie ajoutée est la plus récente, donc en première position
        GameHistoryService.Entry first = lastList.get(0);
        assertThat(first.outcome).isEqualTo("OUTCOME_14");
    }

    // --- recentForUser() (lecture depuis le cache) ---

    @Test
    void recentForUser_shouldReturnSortedHistoryFromCache() throws Exception {
        Utilisateur u = buildUser();
        String game = "roulette";

        when(repo.findByUtilisateurIdAndGame(1L, game)).thenReturn(Optional.empty());
        when(repo.save(any(GameHistoryAggregate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("json");

        // On enregistre deux parties successives pour remplir le cache
        historyService.record(u, game, "FIRST", 50L, 0L, 0);
        Thread.sleep(5); // petites ms pour garantir un createdAt différent
        historyService.record(u, game, "SECOND", 50L, 100L, 2);

        var list = historyService.recentForUser(u, 10);

        assertThat(list).hasSize(2);
        // Les plus récentes d'abord
        assertThat(list.get(0).outcome).isEqualTo("SECOND");
        assertThat(list.get(1).outcome).isEqualTo("FIRST");
        assertThat(list.get(0).createdAt).isNotNull();
    }

    // --- recentForUserByGame() (fallback DB si pas en cache) ---

    @Test
    void recentForUserByGame_shouldLoadFromDbWhenNotInCache_andThenUseCache() throws Exception {
        Utilisateur u = buildUser();
        String game = "blackjack";

        // On prépare un aggregate avec du JSON
        GameHistoryAggregate ag = new GameHistoryAggregate();
        ag.setUtilisateurId(1L);
        ag.setGame(game);
        ag.setEntriesJson("json-data");
        ag.setUpdatedAt(LocalDateTime.now());

        when(repo.findByUtilisateurIdAndGame(1L, game))
                .thenReturn(Optional.of(ag));

        // On prépare la liste des entries renvoyée par l'ObjectMapper
        GameHistoryService.Entry e1 = new GameHistoryService.Entry();
        e1.id = 1000L;
        e1.createdAt = 1000L;
        e1.outcome = "WIN";
        e1.montantJoue = 100L;
        e1.montantGagne = 200L;
        e1.multiplier = 2;

        GameHistoryService.Entry e2 = new GameHistoryService.Entry();
        e2.id = 500L;
        e2.createdAt = 500L;
        e2.outcome = "LOSE";
        e2.montantJoue = 50L;
        e2.montantGagne = 0L;
        e2.multiplier = 0;

        List<GameHistoryService.Entry> entries = List.of(e1, e2);

        // stub readValue pour n'importe quel TypeReference
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(entries);

        // 1er appel : pas dans le cache, donc va chercher en DB
        var list1 = historyService.recentForUserByGame(u, game, 10);

        assertThat(list1).hasSize(2);
        // Tri par createdAt desc => e1 puis e2
        assertThat(list1.get(0).outcome).isEqualTo("WIN");
        assertThat(list1.get(0).montantGagne).isEqualTo(200L);
        assertThat(list1.get(1).outcome).isEqualTo("LOSE");

        // 2e appel : doit passer par le cache, pas re-appeler le repo
        var list2 = historyService.recentForUserByGame(u, game, 10);

        verify(repo, times(1)).findByUtilisateurIdAndGame(1L, game);
        assertThat(list2).hasSize(2);
    }

    // --- deleteAllForUser() ---

    @Test
    void deleteAllForUser_shouldDeleteUserHistoryAndUserItself() {
        Utilisateur u = buildUser();

        GameHistoryAggregate ag1 = new GameHistoryAggregate();
        ag1.setUtilisateurId(1L);
        ag1.setGame("mines");
        ag1.setEntriesJson("[]");
        ag1.setUpdatedAt(LocalDateTime.now());

        GameHistoryAggregate ag2 = new GameHistoryAggregate();
        ag2.setUtilisateurId(1L);
        ag2.setGame("roulette");
        ag2.setEntriesJson("[]");
        ag2.setUpdatedAt(LocalDateTime.now());

        List<GameHistoryAggregate> ags = List.of(ag1, ag2);

        when(repo.findByUtilisateurId(1L)).thenReturn(ags);

        historyService.deleteAllForUser(u);

        // Supprime en DB côté utilisateur
        verify(utilisateurRepo).deleteById(1L);
        // Supprime tous les aggregates d'historique
        verify(repo).deleteAll(ags);
    }
}
