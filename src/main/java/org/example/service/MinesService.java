package org.example.service;

import org.example.model.Utilisateur;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MinesService {

    public static final int GRID = 25;
    private static final double HOUSE_EDGE = 0.98;
//    private static final long TTL_MS = 60 * 1000L; // 1 minute avant expiration auto

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Round> rounds = new ConcurrentHashMap<>();
    private final Map<String, String> sessionByUser = new ConcurrentHashMap<>();

    public static final class Round {
        public final String id;
        public final String email;
        public final long mise;
        public final int mines;
        public final Set<Integer> bombs;
        public final Set<Integer> safes;
        public boolean active = true;
        public final long createdAt = System.currentTimeMillis();

        public Round(String id, String email, long mise, int mines, Set<Integer> bombs) {
            this.id = id;
            this.email = email;
            this.mise = mise;
            this.mines = mines;
            this.bombs = bombs;
            this.safes = new LinkedHashSet<>();
        }
    }

    /** 🟩 Démarrer une partie */
    public synchronized Round start(Utilisateur u, long montant, int mines) {
        String email = u.getEmail();
        if (montant <= 0) throw new IllegalArgumentException("Montant invalide");

        // 🔥 Si une partie est encore en mémoire ou récente → on la nettoie complètement
        cleanupStaleSession(email);

        int m = clampMines(mines);
        Set<Integer> bombs = new LinkedHashSet<>();
        while (bombs.size() < m) bombs.add(random.nextInt(GRID));

        String id = UUID.randomUUID().toString();
        Round r = new Round(id, email, montant, m, bombs);
        rounds.put(id, r);
        sessionByUser.put(email, id);
        return r;
    }

    /** 🔎 Supprime les anciennes parties bloquées */
    private synchronized void cleanupStaleSession(String email) {
        String id = sessionByUser.get(email);
        if (id == null) return;

        Round r = rounds.get(id);
        if (r == null) {
            sessionByUser.remove(email);
            return;
        }

//        long age = System.currentTimeMillis() - r.createdAt;
        if (!r.active) {
            rounds.remove(id);
            sessionByUser.remove(email);
        } else {
            // 🚫 Partie toujours active et trop récente
            throw new IllegalStateException("Une partie est déjà en cours.");
        }
    }

    /** 🔹 Récupère la partie active si valide */
    public synchronized Round getActiveFor(Utilisateur u) {
        String id = sessionByUser.get(u.getEmail());
        if (id == null) return null;
        Round r = rounds.get(id);
        if (r == null || !r.active) {
            sessionByUser.remove(u.getEmail());
            return null;
        }

        // 💀 expire après TTL
//        if (System.currentTimeMillis() - r.createdAt > TTL_MS) {
//            rounds.remove(id);
//            sessionByUser.remove(u.getEmail());
//            return null;
//        }
        return r;
    }

    /** 💎 Clique sur une case */
    public synchronized PickResult pick(Utilisateur u, String sessionId, int index) {
        Round r = mustGetOwnedActive(u, sessionId);
        int i = clampIndex(index);

        if (r.safes.contains(i)) {
            return buildPickResult(r, i, false, false);
        }

        if (r.bombs.contains(i)) {
            // Bombe cliquée (même si déjà cliquée avant)
            r.active = false;
            rounds.remove(r.id);
            sessionByUser.remove(u.getEmail());
            return buildPickResult(r, i, true, true);

    } else {
            // safe
            r.safes.add(i);
            boolean finished = r.safes.size() == (GRID - r.mines);
            return buildPickResult(r, i, false, finished);
        }
    }

    public static final class PickResult {
        public final boolean bomb;
        public final int index;
        public final int safeCount;
        public final int mines;
        public final double currentMultiplier;
        public final long potentialPayout;
        public final boolean finished;
        public final List<Integer> bombsToReveal;
        public final long mise;

        public PickResult(
                boolean bomb, int index, int safeCount, int mines,
                double currentMultiplier, long potentialPayout, boolean finished,
                List<Integer> bombsToReveal, long mise) {
            this.bomb = bomb;
            this.index = index;
            this.safeCount = safeCount;
            this.mines = mines;
            this.currentMultiplier = currentMultiplier;
            this.potentialPayout = potentialPayout;
            this.finished = finished;
            this.bombsToReveal = bombsToReveal;
            this.mise = mise;
        }
    }

    private PickResult buildPickResult(Round r, int index, boolean bomb, boolean finished) {
        int k = r.safes.size();
        double mult = bomb ? 0.0 : multiplierFor(r.mines, k);
        long potential = (long) Math.floor(r.mise * mult);
        List<Integer> bombsList = bomb ? new ArrayList<>(r.bombs) : List.of();
        return new PickResult(
                bomb, index, k, r.mines,
                round4(mult), potential, finished,
                bombsList, r.mise
        );
    }

    /** 💰 Encaissement */
    public synchronized CashoutResult cashout(Utilisateur u, String sessionId) {
        Round r = mustGetOwnedActive(u, sessionId);
        int k = r.safes.size();
        if (k <= 0) throw new IllegalStateException("Aucun diamant révélé.");
        double mult = multiplierFor(r.mines, k);
        long payout = (long) Math.floor(r.mise * mult);
        r.active = false;
        rounds.remove(r.id);
        sessionByUser.remove(u.getEmail());
        return new CashoutResult(true, k, round4(mult), payout, new ArrayList<>(r.bombs), r.mines, r.mise);
    }

    public static final class CashoutResult {
        public final boolean ok;
        public final int safeCount;
        public final double multiplier;
        public final long payout;
        public final List<Integer> bombs;
        public final int mines;
        public final long mise;
        public CashoutResult(boolean ok, int safeCount, double multiplier, long payout,
                             List<Integer> bombs, int mines, long mise) {
            this.ok = ok;
            this.safeCount = safeCount;
            this.multiplier = multiplier;
            this.payout = payout;
            this.bombs = bombs;
            this.mines = mines;
            this.mise = mise;
        }
    }

    /** Multiplicateurs */
    public Map<Integer, Double> tableFor(int mines) {
        int m = clampMines(mines);
        int safe = GRID - m;
        Map<Integer, Double> map = new LinkedHashMap<>();
        double numer = 1, denom = 1;
        for (int k = 1; k <= safe; k++) {
            numer *= (safe - (k - 1));
            denom *= (GRID - (k - 1));
            double pk = denom > 0 ? numer / denom : 0;
            double mult = pk > 0 ? (1.0 / pk) * HOUSE_EDGE : 0.0;
            map.put(k, round4(mult));
        }
        return map;
    }

    public double multiplierFor(int mines, int safeCount) {
        if (safeCount <= 0) return 0.0;
        Map<Integer, Double> t = tableFor(mines);
        return t.getOrDefault(safeCount, 0.0);
    }

    /** Nettoyage auto (chaque 15 secondes) */
    @Scheduled(fixedDelay = 15000)
    public void cleanupOldRounds() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Round>> it = rounds.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Round> e = it.next();
            Round r = e.getValue();
//            if (!r.active || now - r.createdAt > TTL_MS) {
//                it.remove();
//                sessionByUser.remove(r.email);
//            }
        }
    }

    /** 🔒 Utilitaires */
    private Round mustGetOwnedActive(Utilisateur u, String sessionId) {
        Round r = rounds.get(sessionId);
        if (r == null || !r.active)
            throw new IllegalStateException("Session invalide ou terminée");
        if (!Objects.equals(r.email, u.getEmail()))
            throw new IllegalStateException("Session non autorisée");
        return r;
    }

    public synchronized void resetSession(String email) { String id = sessionByUser.remove(email); if (id != null) rounds.remove(id); }

    private int clampMines(int mines) {
        return Math.min(24, Math.max(1, mines));
    }

    private int clampIndex(int idx) {
        return Math.min(GRID - 1, Math.max(0, idx));
    }

    private double round4(double d) {
        return Math.floor(d * 10000.0) / 10000.0;
    }
}
