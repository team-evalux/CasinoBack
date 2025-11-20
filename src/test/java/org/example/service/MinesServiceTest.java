package org.example.service;

import org.example.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

public class MinesServiceTest {

    MinesService service;
    Utilisateur user;

    @BeforeEach
    void setup() {
        service = new MinesService();
        user = new Utilisateur();
        user.setEmail("test@example.com");
    }

    // ------------------------------------------------------------
    // start()
    // ------------------------------------------------------------
    @Test
    void start_ok() {
        MinesService.Round r = service.start(user, 100, 5);

        assertThat(r.email).isEqualTo("test@example.com");
        assertThat(r.mines).isEqualTo(5);
        assertThat(r.bombs).hasSize(5);
        assertThat(r.active).isTrue();
    }

    @Test
    void start_montantInvalide() {
        assertThatThrownBy(() -> service.start(user, 0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_refuseSiPartieActiveExiste() {
        service.start(user, 100, 5);

        assertThatThrownBy(() -> service.start(user, 200, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà en cours");
    }

    @Test
    void start_nettoiePartieInactive() {
        MinesService.Round r = service.start(user, 100, 5);
        r.active = false; // simule partie terminée

        MinesService.Round r2 = service.start(user, 200, 3);
        assertThat(r2.mise).isEqualTo(200);
        assertThat(r2.mines).isEqualTo(3);
    }

    // ------------------------------------------------------------
    // getActiveFor()
    // ------------------------------------------------------------
    @Test
    void getActive_ok() {
        MinesService.Round r = service.start(user, 100, 5);
        MinesService.Round found = service.getActiveFor(user);

        assertThat(found).isNotNull();
        assertThat(found.id).isEqualTo(r.id);
    }

    @Test
    void getActive_retourneNullSiInactive() {
        MinesService.Round r = service.start(user, 100, 5);
        r.active = false;

        assertThat(service.getActiveFor(user)).isNull();
    }

    // ------------------------------------------------------------
    // pick()
    // ------------------------------------------------------------
    @Test
    void pick_safe() {
        MinesService.Round r = service.start(user, 100, 1);

        // s'assurer que 0 n'est PAS une bombe
        while (r.bombs.contains(0)) {
            service.resetSession(user.getEmail());
            r = service.start(user, 100, 1);
        }

        MinesService.PickResult res = service.pick(user, r.id, 0);

        assertThat(res.bomb).isFalse();
        assertThat(res.safeCount).isEqualTo(1);
        assertThat(r.safes).contains(0);
    }

    @Test
    void pick_bomb() {
        service.start(user, 100, 5);
        MinesService.Round r = service.getActiveFor(user);

        int bombIndex = r.bombs.iterator().next();

        MinesService.PickResult res = service.pick(user, r.id, bombIndex);

        assertThat(res.bomb).isTrue();
        assertThat(res.finished).isTrue();

        // après une bombe → la session doit avoir été supprimée
        assertThat(service.getActiveFor(user)).isNull();
    }


    @Test
    void pick_clampIndex() {
        MinesService.Round r = service.start(user, 100, 5);

        MinesService.PickResult res = service.pick(user, r.id, 999);
        // clamp → maximum GRID-1 = 24
        assertThat(res.index).isEqualTo(24);
    }

    @Test
    void pick_surCaseDejaCliquee() {
        MinesService.Round r = service.start(user, 100, 1);

        int safe = 0;
        while (r.bombs.contains(safe)) safe++;

        service.pick(user, r.id, safe);
        MinesService.PickResult res = service.pick(user, r.id, safe);

        assertThat(res.safeCount).isEqualTo(1);
        assertThat(res.bomb).isFalse();
    }

    @Test
    void pick_finishedQuandPlusDeSafe() {
        MinesService.Round r = service.start(user, 10, 24); // 1 safe seulement

        // trouver l’unique safe
        int safe = -1;
        for (int i = 0; i < 25; i++) {
            if (!r.bombs.contains(i)) {
                safe = i;
                break;
            }
        }

        MinesService.PickResult res = service.pick(user, r.id, safe);

        assertThat(res.finished).isTrue();
    }

    // ------------------------------------------------------------
    // cashout()
    // ------------------------------------------------------------
    @Test
    void cashout_ok() {
        MinesService.Round r = service.start(user, 100, 5);

        // trouve une safe
        int safe = 0;
        while (r.bombs.contains(safe)) safe++;

        service.pick(user, r.id, safe);
        MinesService.CashoutResult out = service.cashout(user, r.id);

        assertThat(out.ok).isTrue();
        assertThat(out.payout).isGreaterThan(0);
        assertThat(service.getActiveFor(user)).isNull();
    }

    @Test
    void cashout_refuseSansSafe() {
        MinesService.Round r = service.start(user, 100, 5);

        assertThatThrownBy(() -> service.cashout(user, r.id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Aucun diamant");
    }

    // ------------------------------------------------------------
    // multiplier & tableFor
    // ------------------------------------------------------------
    @Test
    void tableFor_tailleCorrecte() {
        Map<Integer, Double> t = service.tableFor(5);

        assertThat(t).isNotEmpty();
        assertThat(t.keySet()).contains(1);
    }

    @Test
    void multiplierFor_ok() {
        Map<Integer, Double> t = service.tableFor(5);

        double m = service.multiplierFor(5, 1);

        assertThat(m).isEqualTo(t.get(1));
    }

    // ------------------------------------------------------------
    // resetSession()
    // ------------------------------------------------------------
    @Test
    void resetSession_supprimeRound() {
        MinesService.Round r = service.start(user, 100, 5);

        service.resetSession(user.getEmail());

        assertThat(service.getActiveFor(user)).isNull();
    }

    // ------------------------------------------------------------
    // clamp utilitaires
    // ------------------------------------------------------------
    @Test
    void clampMines_ok() {
        assertThat(service.tableFor(0)).isNotEmpty();
        assertThat(service.tableFor(50)).isNotEmpty();
    }

    @Test
    void clampIndex_ok() {
        MinesService.Round r = service.start(user, 10, 5);
        MinesService.PickResult res = service.pick(user, r.id, -50);

        assertThat(res.index).isEqualTo(0);
    }
}
