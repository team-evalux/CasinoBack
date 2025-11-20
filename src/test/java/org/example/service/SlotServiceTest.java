package org.example.service;

import org.example.model.SlotConfig;
import org.example.repo.SlotConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SlotServiceTest {

    @Mock
    private SlotConfigRepository repo;

    private SlotService service;

    @BeforeEach
    void setUp() {
        service = new SlotService(repo);
    }

    // -------------------------------------------------------
    // TEST 1 — initFromDb : aucun SlotConfig existant -> defaults créés
    // -------------------------------------------------------
    @Test
    void initFromDb_creeLesDefaultsSiVide() {
        when(repo.findAll()).thenReturn(Collections.emptyList());

        service.initFromDb();

        // On récupère toutes les configs runtime
        Map<Integer, SlotService.SlotRuntimeConfig> cfgs = service.getAllRuntimeConfigs();

        assertThat(cfgs).hasSize(3);
        assertThat(cfgs.keySet()).containsExactlyInAnyOrder(3,4,5);

        SlotService.SlotRuntimeConfig cfg3 = cfgs.get(3);
        assertThat(cfg3.symbols).contains("🍒","🍋","🍊","⭐","7️⃣");

        // vérifie l'appel à save
        verify(repo, atLeastOnce()).save(any(SlotConfig.class));
    }

    // -------------------------------------------------------
    // TEST 2 — initFromDb : charge une config existante
    // -------------------------------------------------------
    @Test
    void initFromDb_chargeConfigDepuisLaDB() throws Exception {
        String symbolsJson = "[\"🍒\",\"🍋\"]";
        String reelJson = "[[100,100],[50,150]]";
        String payoutsJson = "{\"3\":10}";
        String svJson = "{\"🍒\":2.0,\"🍋\":1.0}";

        SlotConfig sc = new SlotConfig(symbolsJson, reelJson, payoutsJson, svJson, 2);
        sc.setReelsCount(2);

        when(repo.findAll()).thenReturn(List.of(sc));

        service.initFromDb();

        SlotService.SlotRuntimeConfig cfg = service.getRuntimeConfigFor(2);

        assertThat(cfg.symbols).containsExactly("🍒","🍋");
        assertThat(cfg.reelWeights).hasSize(2);
        assertThat(cfg.symbolValues.get("🍒")).isEqualTo(2.0);
    }

    // -------------------------------------------------------
    // TEST 3 — buildDefaultConfig
    // -------------------------------------------------------
    @Test
    void buildDefaultConfig_genereUneConfigValide() {
        SlotService.SlotRuntimeConfig cfg =
                invokeBuildDefault(3);

        assertThat(cfg.symbols).hasSize(5);
        assertThat(cfg.reelWeights).hasSize(3);
        assertThat(cfg.payouts.get(3)).isEqualTo(10);
    }

    private SlotService.SlotRuntimeConfig invokeBuildDefault(int reels) {
        // accès via une méthode utilitaire privée -> via findNearestConfigOrDefault hack :
        // ou bien: on appelle initFromDb (vide) puis on récupère une config
        when(repo.findAll()).thenReturn(Collections.emptyList());
        service.initFromDb();
        return service.getRuntimeConfigFor(reels);
    }

    // -------------------------------------------------------
    // TEST 4 — spinForReels : sélectionne bien des symboles
    // -------------------------------------------------------
    @Test
    void spinForReels_genereUneListeDeSymboles() {
        when(repo.findAll()).thenReturn(Collections.emptyList());
        service.initFromDb();

        List<String> reels = service.spinForReels(3);

        assertThat(reels).hasSize(3);
        assertThat(reels).allMatch(s -> List.of("🍒","🍋","🍊","⭐","7️⃣").contains(s));
    }

    // -------------------------------------------------------
    // TEST 5 — computePayout : cas de gain
    // -------------------------------------------------------
    @Test
    void computePayout_retourneGainCorrect() {
        when(repo.findAll()).thenReturn(Collections.emptyList());
        service.initFromDb();

        // 3 fois 🍒 -> combinaison gagnante (payout 10 * 1.0)
        List<String> reels = List.of("🍒","🍒","🍒");

        long res = service.computePayout(reels, 10);

        assertThat(res).isEqualTo(100); // 10 * 10 * 1.0
    }

    // -------------------------------------------------------
    // TEST 6 — computePayout : aucun gain
    // -------------------------------------------------------
    @Test
    void computePayout_aucunGain() {
        when(repo.findAll()).thenReturn(Collections.emptyList());
        service.initFromDb();

        List<String> reels = List.of("🍒","🍋","🍊");

        long res = service.computePayout(reels, 10);

        assertThat(res).isZero();
    }

    // -------------------------------------------------------
    // TEST 7 — updateConfigForReels : met à jour repository
    // -------------------------------------------------------
    @Test
    void updateConfigForReels_metAJourEtPersist() {
        when(repo.findAll()).thenReturn(Collections.emptyList());
        service.initFromDb();

        List<String> newSymbols = List.of("A","B","C");
        List<List<Integer>> newWeights = List.of(
                List.of(100,50,50),
                List.of(80,60,60),
                List.of(70,70,60)
        );
        Map<Integer,Integer> payouts = Map.of(3,20,2,5);
        Map<String,Double> sv = Map.of("A", 1.5, "B", 1.0, "C", 0.8);

        service.updateConfigForReels(3, newSymbols, newWeights, payouts, sv);

        SlotService.SlotRuntimeConfig cfg = service.getRuntimeConfigFor(3);

        assertThat(cfg.symbols).containsExactly("A","B","C");
        assertThat(cfg.payouts.get(3)).isEqualTo(20);
        assertThat(cfg.symbolValues.get("A")).isEqualTo(1.5);

        verify(repo, atLeastOnce()).save(any());
    }
}
