package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.RouletteConfig;
import org.example.repo.RouletteConfigRepository;
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
class RouletteServiceTest {

    @Mock
    RouletteConfigRepository repo;

    RouletteService service;

    @BeforeEach
    void setup() {
        service = new RouletteService(repo);
    }

    // -------------------------------------------------------------------------
    // initFromDb
    // -------------------------------------------------------------------------
    @Test
    void initFromDb_sansConfig_creeConfigParDefaut() {
        when(repo.findAll()).thenReturn(Collections.emptyList());

        service.initFromDb();

        ArgumentCaptor<RouletteConfig> cap = ArgumentCaptor.forClass(RouletteConfig.class);
        verify(repo).save(cap.capture());

        RouletteConfig cfg = cap.getValue();
        assertThat(cfg.getWeightsJson()).isNull();
        assertThat(service.getCustomWeights()).isNull();
    }

    @Test
    void initFromDb_chargeConfigJsonValide() throws Exception {
        Map<String,Integer> jsonMap = Map.of("5", 10, "7", 50);
        String jsonStr = new ObjectMapper().writeValueAsString(jsonMap);

        RouletteConfig rc = new RouletteConfig(jsonStr);
        when(repo.findAll()).thenReturn(List.of(rc));

        service.initFromDb();

        Map<Integer,Integer> weights = service.getCustomWeights();
        assertThat(weights).containsEntry(5, 10);
        assertThat(weights).containsEntry(7, 50);
    }

    @Test
    void initFromDb_jsonInvalide_retourneNull() {
        RouletteConfig rc = new RouletteConfig("json_invalide");
        when(repo.findAll()).thenReturn(List.of(rc));

        service.initFromDb();

        assertThat(service.getCustomWeights()).isNull();
    }

    // -------------------------------------------------------------------------
    // setCustomWeights
    // -------------------------------------------------------------------------
    @Test
    void setCustomWeights_nullReset() {
        service.setCustomWeights(null);

        assertThat(service.getCustomWeights()).isNull();
        verify(repo).save(any());
    }

    @Test
    void setCustomWeights_nettoieLesValeursInvalides() {
        Map<Integer,Integer> input = new HashMap<>();
        input.put(-3, 10);     // invalide
        input.put(40, 10);     // invalide
        input.put(5, 0);       // invalide (0)
        input.put(12, -5);     // invalide (neg)
        input.put(18, 20);     // valide

        service.setCustomWeights(input);

        Map<Integer,Integer> result = service.getCustomWeights();
        assertThat(result).containsOnly(Map.entry(18, 20));

        verify(repo, atLeastOnce()).save(any());
    }

    @Test
    void setCustomWeights_validePersisteJson() throws Exception {
        Map<Integer,Integer> input = Map.of(5, 10, 7, 20);

        service.setCustomWeights(input);

        ArgumentCaptor<RouletteConfig> cap = ArgumentCaptor.forClass(RouletteConfig.class);
        verify(repo, atLeastOnce()).save(cap.capture());

        String json = cap.getValue().getWeightsJson();
        Map<String,Integer> parsed =
                new ObjectMapper().readValue(json, new TypeReference<>(){});

        assertThat(parsed).containsEntry("5", 10);
    }

    // -------------------------------------------------------------------------
    // resetWeights
    // -------------------------------------------------------------------------
    @Test
    void resetWeights_reinitialise() {
        service.setCustomWeights(Map.of(5,100));

        service.resetWeights();

        assertThat(service.getCustomWeights()).isNull();
        verify(repo, atLeastOnce()).save(any());
    }

    // -------------------------------------------------------------------------
    // tirerNumero
    // -------------------------------------------------------------------------
    @Test
    void tirerNumero_uniforme_retourneNombreEntre0Et36() {
        service.resetWeights();

        int n = service.tirerNumero();
        assertThat(n).isBetween(0,36);
    }

    @Test
    void tirerNumero_pondere_renvoieToujoursLaCleUnique() {
        service.setCustomWeights(Map.of(17, 9999));

        for (int i = 0; i < 20; i++) {
            assertThat(service.tirerNumero()).isEqualTo(17);
        }
    }

    @Test
    void tirerNumero_pondere_multipesChoix() {
        service.setCustomWeights(Map.of(5, 1, 7, 1, 9, 1));

        int n = service.tirerNumero();
        assertThat(List.of(5,7,9)).contains(n);
    }

    // -------------------------------------------------------------------------
    // couleurPour
    // -------------------------------------------------------------------------
    @Test
    void couleurPour_green() {
        assertThat(service.couleurPour(0)).isEqualTo("green");
    }

    @Test
    void couleurPour_red() {
        assertThat(service.couleurPour(1)).isEqualTo("red");
        assertThat(service.couleurPour(3)).isEqualTo("red");
    }

    @Test
    void couleurPour_black() {
        assertThat(service.couleurPour(2)).isEqualTo("black");
        assertThat(service.couleurPour(4)).isEqualTo("black");
    }

    // -------------------------------------------------------------------------
    // estGagnant
    // -------------------------------------------------------------------------
    @Test
    void estGagnant_straight() {
        assertThat(service.estGagnant("straight","5",5)).isTrue();
        assertThat(service.estGagnant("straight","7",5)).isFalse();
    }

    @Test
    void estGagnant_color() {
        assertThat(service.estGagnant("color","red",1)).isTrue();
        assertThat(service.estGagnant("color","black",1)).isFalse();
    }

    @Test
    void estGagnant_parity() {
        assertThat(service.estGagnant("parity","even",4)).isTrue();
        assertThat(service.estGagnant("parity","odd",4)).isFalse();
    }

    @Test
    void estGagnant_range() {
        assertThat(service.estGagnant("range","low",5)).isTrue();
        assertThat(service.estGagnant("range","high",5)).isFalse();
    }

    @Test
    void estGagnant_dozen() {
        assertThat(service.estGagnant("dozen","1",5)).isTrue();
        assertThat(service.estGagnant("dozen","2",5)).isFalse();
    }

    @Test
    void estGagnant_invalide() {
        assertThat(service.estGagnant(null,"x",5)).isFalse();
        assertThat(service.estGagnant("color",null,5)).isFalse();
        assertThat(service.estGagnant("abc","x",5)).isFalse();
    }

    // -------------------------------------------------------------------------
    // payoutMultiplier
    // -------------------------------------------------------------------------
    @Test
    void payoutMultiplier_valeurs() {
        assertThat(service.payoutMultiplier("straight")).isEqualTo(35L);
        assertThat(service.payoutMultiplier("color")).isEqualTo(2L);
        assertThat(service.payoutMultiplier("parity")).isEqualTo(2L);
        assertThat(service.payoutMultiplier("range")).isEqualTo(2L);
        assertThat(service.payoutMultiplier("dozen")).isEqualTo(3L);
        assertThat(service.payoutMultiplier("invalide")).isEqualTo(0L);
        assertThat(service.payoutMultiplier(null)).isEqualTo(0L);
    }
}
