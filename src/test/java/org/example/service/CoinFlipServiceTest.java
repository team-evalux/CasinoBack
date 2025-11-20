package org.example.service;

import org.example.model.CoinFlipConfig;
import org.example.repo.CoinFlipConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoinFlipServiceTest {

    @Mock
    private CoinFlipConfigRepository repo;

    private CoinFlipService coinFlipService;

    @BeforeEach
    void setUp() {
        // repo est bien initialisé par Mockito à ce moment-là
        coinFlipService = new CoinFlipService(repo, 0.5);
    }

    // --- CONSTRUCTEUR / CLAMP ---

    @Test
    void constructor_shouldClampInitialProbBetween0And1() {
        CoinFlipService s1 = new CoinFlipService(repo, -0.5);
        assertThat(s1.getProbPile()).isEqualTo(0.0);

        CoinFlipService s2 = new CoinFlipService(repo, 1.5);
        assertThat(s2.getProbPile()).isEqualTo(1.0);

        CoinFlipService s3 = new CoinFlipService(repo, 0.3);
        assertThat(s3.getProbPile()).isEqualTo(0.3);
    }

    // --- @PostConstruct initFromDb() ---

    @Test
    void initFromDb_whenNoConfigInDb_shouldCreateOneWithCurrentProb() {
        when(repo.findAll()).thenReturn(Collections.emptyList());

        CoinFlipService service = new CoinFlipService(repo, 0.7);

        service.initFromDb();

        assertThat(service.getProbPile()).isEqualTo(0.7);

        ArgumentCaptor<CoinFlipConfig> captor = ArgumentCaptor.forClass(CoinFlipConfig.class);
        verify(repo).save(captor.capture());
        CoinFlipConfig saved = captor.getValue();
        assertThat(saved.getProbPile()).isEqualTo(0.7);
    }

    @Test
    void initFromDb_whenConfigExists_shouldLoadFirstConfig() {
        CoinFlipConfig existing = new CoinFlipConfig(0.8);
        when(repo.findAll()).thenReturn(List.of(existing));

        CoinFlipService service = new CoinFlipService(repo, 0.2);

        service.initFromDb();

        assertThat(service.getProbPile()).isEqualTo(0.8);
        verify(repo, never()).save(any(CoinFlipConfig.class));
    }

    // --- setProbPile() ---

    @Test
    void setProbPile_whenNoConfigInDb_shouldCreateConfigWithClampedValue() {
        when(repo.findAll()).thenReturn(Collections.emptyList());

        coinFlipService.setProbPile(2.0);  // > 1.0 → clampé à 1.0

        assertThat(coinFlipService.getProbPile()).isEqualTo(1.0);

        ArgumentCaptor<CoinFlipConfig> captor = ArgumentCaptor.forClass(CoinFlipConfig.class);
        verify(repo).save(captor.capture());
        CoinFlipConfig saved = captor.getValue();
        assertThat(saved.getProbPile()).isEqualTo(1.0);
    }

    @Test
    void setProbPile_whenConfigExists_shouldUpdateAndSaveFirstConfig() {
        CoinFlipConfig existing = new CoinFlipConfig(0.3);
        when(repo.findAll()).thenReturn(List.of(existing));

        coinFlipService.setProbPile(-1.0);  // < 0.0 → clampé à 0.0

        assertThat(coinFlipService.getProbPile()).isEqualTo(0.0);

        ArgumentCaptor<CoinFlipConfig> captor = ArgumentCaptor.forClass(CoinFlipConfig.class);
        verify(repo).save(captor.capture());
        CoinFlipConfig saved = captor.getValue();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getProbPile()).isEqualTo(0.0);
    }

    // --- tirer() ---

    @Test
    void tirer_shouldReturnOnlyPileOrFace() {
        when(repo.findAll()).thenReturn(Collections.emptyList());
        // on ne touche pas à la DB ici, on teste juste la sortie
        coinFlipService.setProbPile(0.5);

        for (int i = 0; i < 100; i++) {
            String outcome = coinFlipService.tirer();
            assertThat(outcome).isIn("pile", "face");
        }
    }
}
