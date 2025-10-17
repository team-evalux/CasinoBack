// src/main/java/org/example/service/BjTableService.java
package org.example.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.example.dto.blackjack.*;
import org.example.model.Utilisateur;
import org.example.model.blackjack.*;
import org.example.repo.BjTableRepository;
import org.example.repo.UtilisateurRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class BjTableService {

    private final WalletService walletService;
    private final UtilisateurRepository utilisateurRepo;
    private final BjTableRepository bjTableRepository;
    private final SimpMessagingTemplate broker;
    private final GameHistoryService historyService;

    // runtime tables (id DB -> runtime object)
    private final Map<Long, BjTable> tables = new ConcurrentHashMap<>();

    // In BjTableService
    private final Map<String, String> displayNameCache = new ConcurrentHashMap<>();


    // emails autorisés pour joindre une table privée (tableId -> set d'emails)
    private final Map<Long, Set<String>> privateAccess = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    // mapping email -> tableId (un utilisateur assis sur au plus une table)
    private final Map<String, Long> userTable = new ConcurrentHashMap<>();

    // timers pour nettoyage après déconnexion (email -> scheduledFuture)
    private final Map<String, ScheduledFuture<?>> disconnectTimers = new ConcurrentHashMap<>();

    private static final long BETTING_MS = 10_000;
    private static final long TURN_MS    = 20_000;
    private static final long RESULT_MS = 10_000;
    private static final long DISCONNECT_GRACE_MS = 120_000;


    // ------------------------------------------------------------------------
    // Helpers

    private Map<String, Object> m(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            String k = (String) kv[i];
            Object v = kv[i + 1];
            out.put(k, v);
        }
        return out;
    }

    // Helper: affiche le pseudo si on trouve l'utilisateur, sinon fallback sur la partie locale de l'email ou null
    private String displayNameForEmail(String email) {
        if (email == null) return null;

        return displayNameCache.computeIfAbsent(email, e -> {
            try {
                return utilisateurRepo.findByEmail(e)
                        .map(Utilisateur::getPseudo)
                        .orElseGet(() -> {
                            int at = e.indexOf('@');
                            return at > 0 ? e.substring(0, at) : e;
                        });
            } catch (Exception ex) {
                int at = e.indexOf('@');
                return at > 0 ? e.substring(0, at) : e;
            }
        });
    }


    // Helper: transforme la Map<Integer,Seat> en Map<Integer, Map<String,Object>> serialisable,
// en ajoutant "displayName" pour chaque siège.
    private Map<Integer, Map<String, Object>> seatsPayload(BjTable t) {
        Map<Integer, Map<String,Object>> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Integer idx = e.getKey();
            Seat s = e.getValue();
            Map<String,Object> seatMap = new LinkedHashMap<>();
            if (s != null) {
                seatMap.put("userId", s.getUserId());
                seatMap.put("email", s.getEmail());
                seatMap.put("displayName", displayNameForEmail(s.getEmail()));
                seatMap.put("status", s.getStatus() != null ? s.getStatus().name() : null);
                // hand
                Map<String,Object> handMap = new LinkedHashMap<>();
                handMap.put("cards", s.getHand().getCards());
                handMap.put("standing", s.getHand().isStanding());
                handMap.put("busted", s.getHand().isBusted());
                handMap.put("blackjack", s.getHand().isBlackjack());
                handMap.put("bet", s.getHand().getBet());
                handMap.put("total", s.getHand().bestTotal());
                seatMap.put("hand", handMap);
            } else {
                seatMap.put("userId", null);
                seatMap.put("email", null);
                seatMap.put("displayName", null);
                seatMap.put("status", "EMPTY");
                seatMap.put("hand", Map.of("cards", List.of(), "standing", false, "busted", false, "blackjack", false, "bet", 0, "total", 0));
            }
            out.put(idx, seatMap);
        }
        return out;
    }


    private void broadcast(BjTable t, String type, Object payload) {
        TableEvent ev = TableEvent.builder().type(type).payload(payload).build();
        if (!t.isPrivate()) {
            broker.convertAndSend("/topic/bj/table/" + t.getId(), ev);
            return;
        }
        Set<String> allowed = new HashSet<>();
        if (t.getCreatorEmail() != null) allowed.add(t.getCreatorEmail());
        for (Seat s : t.getSeats().values()) if (s.getEmail() != null) allowed.add(s.getEmail());
        Set<String> extra = privateAccess.get(t.getId());
        if (extra != null) allowed.addAll(extra);
        for (String email : allowed) {
            broker.convertAndSendToUser(email, "/queue/bj/table/" + t.getId(), ev);
        }
    }

    private void broadcastState(BjTable t) {
        broadcast(t, "TABLE_STATE", m(
                "tableId",          t.getId(),
                "phase",            t.getPhase(),
                "deadline",         t.getPhaseDeadlineEpochMs(),
                "seats",            seatsPayload(t),
                "dealer",           t.getDealer(),
                "currentSeatIndex", t.getCurrentSeatIndex(),
                "isPrivate",        t.isPrivate(),
                "creatorEmail",     t.getCreatorEmail(),
                "creatorDisplayName", displayNameForEmail(t.getCreatorEmail()),
                "name",             t.getName(),
                "minBet",           t.getMinBet(),
                "maxBet",           t.getMaxBet()
        ));
    }

    public List<BjTable> listTables() {
        return new ArrayList<>(tables.values());
    }

    private void broadcastLobby() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (BjTable t : tables.values()) {
            list.add(m(
                    "id",           t.getId(),
                    "maxSeats",     t.getMaxSeats(),
                    "isPrivate",    t.isPrivate(),
                    "phase",        t.getPhase() != null ? t.getPhase().name() : "WAITING_FOR_PLAYERS",
                    "name",         t.getName(),
                    "minBet",       t.getMinBet(),
                    "maxBet",       t.getMaxBet(),
                    "creatorEmail", t.getCreatorEmail(),
                    "creatorDisplayName", displayNameForEmail(t.getCreatorEmail())
            ));
        }
        broker.convertAndSend("/topic/bj/lobby", list);
    }


    private BjTable mustTable(Long id) {
        BjTable t = tables.get(id);
        if (t == null) throw new IllegalArgumentException("Table inconnue");
        return t;
    }

    private Seat mustOwnedSeat(BjTable t, int seatIndex, String email) {
        Seat s = t.getSeats().get(seatIndex);
        if (s == null || s.getStatus() == SeatStatus.EMPTY) throw new IllegalStateException("Siège vide");
        if (!Objects.equals(s.getEmail(), email)) throw new IllegalStateException("Pas ton siège");
        return s;
    }

    private Integer firstActiveSeat(BjTable t) {
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            if (s.getHand().getBet() > 0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                return e.getKey();
            }
        }
        return null;
    }

    private void cancelDisconnectTimer(String email) {
        ScheduledFuture<?> f = disconnectTimers.remove(email);
        if (f != null) f.cancel(false);
    }

    private void scheduleDisconnectCleanup(BjTable t, int seatIndex, String email) {
        cancelDisconnectTimer(email);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                Seat s = t.getSeats().get(seatIndex);
                if (s == null) return;
                if (!Objects.equals(s.getEmail(), email)) return;
                if (s.getStatus() != SeatStatus.DISCONNECTED) return;

                // Ne supprimer le siège que si la table est dans un état sûr (pas mid-hand)
                if (t.getPhase() == TablePhase.BETTING || t.getPhase() == TablePhase.PAYOUT) {
                    t.getSeats().put(seatIndex, new Seat());
                    userTable.remove(email);
                    disconnectTimers.remove(email);
                    broadcastState(t);
                    broadcastLobby();
                } else {
                    // si on est en pleine main, on laisse la place et on ne supprime pas tout de suite
                    // l'utilisateur restera DISCONNECTED ; on évite une suppression qui complexifierait la logique
                    disconnectTimers.remove(email);
                }
            }
        }, DISCONNECT_GRACE_MS, TimeUnit.MILLISECONDS);
        disconnectTimers.put(email, future);
    }
    // ------------------------------------------------------------------------
    // PUBLIC API

    @PostConstruct
    private void loadTablesFromDb() {
        List<BjTableEntity> entities = bjTableRepository.findAll();
        for (BjTableEntity e : entities) {
            int seats = (e.getMaxSeats() != null) ? e.getMaxSeats() : 5;
            BjTable t = new BjTable(e.getId(), seats, e.isPrivate(), e.getCode());
            t.setName(e.getName());
            t.setMinBet(e.getMinBet());
            t.setMaxBet(e.getMaxBet());
            t.setCreatorEmail(e.getCreatorEmail());
            t.setCreatedAt(e.getCreatedAt());
            tables.put(t.getId(), t);
            if (t.isPrivate() && t.getCreatorEmail() != null) {
                privateAccess.computeIfAbsent(t.getId(), k -> ConcurrentHashMap.newKeySet()).add(t.getCreatorEmail());
            }
        }
        broadcastLobby();
    }

    /**
     * Crée une table et, si creatorEmail != null, assied automatiquement le créateur au siège 0.
     *
     * NOTE: pour les tables privées le 'code' est obligatoire (selon ta demande).
     */
    public synchronized BjTable createTable(String creatorEmail, boolean isPrivate, String code, int maxSeats,
                                            String name, long minBet, long maxBet) {

        if (creatorEmail != null) {
            // Vérifie si l'utilisateur a déjà une table
            for (BjTable existing : tables.values()) {
                if (creatorEmail.equals(existing.getCreatorEmail())) {
                    throw new IllegalStateException("Vous possédez déjà une table. Fermez-la avant d'en créer une nouvelle.");
                }
            }
        }

        if (isPrivate && (code == null || code.isBlank())) {
            throw new IllegalArgumentException("Code requis pour une table privée");
        }

        // ✅ Limitation du nom à 10 caractères
        if (name != null) {
            name = name.trim();
            if (name.length() > 10) {
                name = name.substring(0, 10);
            }
        }

        // ✅ Si aucun nom fourni, on met "Table de <pseudo>"
        if (name == null || name.isBlank()) {
            String pseudo = utilisateurRepo.findByEmail(creatorEmail)
                    .map(Utilisateur::getPseudo)
                    .orElseGet(() -> {
                        int at = creatorEmail != null ? creatorEmail.indexOf('@') : -1;
                        return at > 0 ? creatorEmail.substring(0, at) : "Inconnu";
                    });
            name = "Table de " + pseudo;
            // on tronque encore une fois si le pseudo est trop long
            if (name.length() > 20) name = name.substring(0, 20);
        }

        // persist entity
        BjTableEntity ent = new BjTableEntity();
        ent.setPrivate(isPrivate);
        ent.setCode(code);
        ent.setMaxSeats(maxSeats);
        ent.setName(name);
        ent.setMinBet(minBet);
        ent.setMaxBet(maxBet);
        ent.setCreatorEmail(creatorEmail);
        ent.setCreatedAt(Instant.now());

        BjTableEntity saved = bjTableRepository.save(ent);

        // create runtime object with DB id
        BjTable t = new BjTable(saved.getId(), Math.max(2, Math.min(7, maxSeats)), isPrivate, code);
        t.setPhase(TablePhase.BETTING);
        t.setPhaseDeadlineEpochMs(5L);
        t.setCurrentSeatIndex(null);
        t.setCreatorEmail(creatorEmail);
        t.setCreatedAt(saved.getCreatedAt());
        t.setName(name);
        t.setMinBet(minBet);
        t.setMaxBet(maxBet);
        t.setLastActiveAt(Instant.now());

        tables.put(t.getId(), t);

        // autorise automatiquement le créateur pour les tables privées
        if (isPrivate && creatorEmail != null) {
            privateAccess.computeIfAbsent(t.getId(), k -> ConcurrentHashMap.newKeySet()).add(creatorEmail);
        }

        // assise automatique du créateur
        if (creatorEmail != null) {
            utilisateurRepo.findByEmail(creatorEmail).ifPresent(u -> {
                Seat seat0 = t.getSeats().get(0);
                seat0.setUserId(u.getId());
                seat0.setEmail(creatorEmail);
                seat0.setStatus(SeatStatus.SEATED);
                userTable.put(creatorEmail, t.getId());
            });
        }

        broadcastState(t);
        broadcastLobby();
        return t;
    }

    public BjTable getTable(Long id) {
        return tables.get(id);
    }

    public List<BjTable> listPublicTables() {
        List<BjTable> out = new ArrayList<>();
        for (BjTable t : tables.values()) if (!t.isPrivate()) out.add(t);
        return out;
    }

    private void markActive(BjTable t) {
        t.setLastActiveAt(Instant.now());
    }


    /**
     * Si msg.tableId est fourni et que la table est privée, on exige le code et on autorise l'email si OK.
     * Si createPrivate=true on exige un code non vide (création).
     */
    public synchronized BjTable joinOrCreate(String email, JoinOrCreateMsg msg) {
        int maxSeats = (msg.getMaxSeats() != null) ? msg.getMaxSeats() : 5;
        String name = msg.getName();
        long minBet = (msg.getMinBet() != null) ? msg.getMinBet() : 0L;
        long maxBet = (msg.getMaxBet() != null) ? msg.getMaxBet() : 0L;

        // --- Création table publique
        if (Boolean.TRUE.equals(msg.getCreatePublic())) {
            return createTable(email, false, null, maxSeats, name, minBet, maxBet);
        }

        // --- Création table privée
        if (Boolean.TRUE.equals(msg.getCreatePrivate())) {
            if (msg.getCode() == null || msg.getCode().isBlank()) {
                throw new IllegalArgumentException("Code requis pour créer une table privée");
            }
            return createTable(email, true, msg.getCode(), maxSeats, name, minBet, maxBet);
        }

        // --- Rejoindre une table existante
        if (msg.getTableId() != null) {
            BjTable t = mustTable(msg.getTableId());
            markActive(t);

            // Vérification code privé
            if (t.isPrivate()) {
                if (msg.getCode() == null || !Objects.equals(msg.getCode(), t.getCode())) {
                    throw new IllegalStateException("Code d'accès invalide pour cette table privée");
                }
                privateAccess.computeIfAbsent(t.getId(), k -> ConcurrentHashMap.newKeySet()).add(email);
            }

            // ✅ Vérifie si déjà assis (évite les doublons, surtout pour le créateur)
            boolean dejaAssis = t.getSeats().values().stream()
                    .anyMatch(s -> email.equals(s.getEmail()));
            if (!dejaAssis) {
                // 🧠 Auto-assise en ordre croissant
                Seat assignedSeat = null;
                for (int i = 0; i < t.getMaxSeats(); i++) {
                    Seat s = t.getSeats().get(i);
                    if (s == null || s.getStatus() == SeatStatus.EMPTY) {
                        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
                        Seat newSeat = new Seat();
                        newSeat.setUserId(u.getId());
                        newSeat.setEmail(email);
                        newSeat.setStatus(SeatStatus.SEATED);
                        t.getSeats().put(i, newSeat);
                        userTable.put(email, t.getId());
                        assignedSeat = newSeat;
                        break;
                    }
                }
                if (assignedSeat == null) {
                    throw new IllegalStateException("Aucun siège disponible sur cette table.");
                }
            }

            broadcastState(t);
            broadcastLobby();
            return t;
        }

        // --- Sinon, rejoindre ou créer une table publique par défaut
        BjTable table = listPublicTables().stream().findFirst()
                .orElseGet(() -> createTable(email, false, null, 5, null, 0L, 0L));

        // ✅ Même protection ici
        boolean dejaAssis = table.getSeats().values().stream()
                .anyMatch(s -> email.equals(s.getEmail()));
        if (!dejaAssis) {
            for (int i = 0; i < table.getMaxSeats(); i++) {
                Seat s = table.getSeats().get(i);
                if (s == null || s.getStatus() == SeatStatus.EMPTY) {
                    Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
                    Seat newSeat = new Seat();
                    newSeat.setUserId(u.getId());
                    newSeat.setEmail(email);
                    newSeat.setStatus(SeatStatus.SEATED);
                    table.getSeats().put(i, newSeat);
                    userTable.put(email, table.getId());
                    break;
                }
            }
        }

        broadcastState(table);
        broadcastLobby();
        return table;
    }



    /**
     * Méthode publique pratique : autorise un email pour une table si le code correspond.
     * Retourne true si autorisé, false sinon.
     * Tu peux appeler cette méthode depuis ton WsController si tu reçois le code séparément.
     */
    public synchronized boolean authorizeEmailForTable(Long tableId, String email, String code) {
        BjTable t = tables.get(tableId);
        if (t == null) return false;
        if (!t.isPrivate()) return true;
        if (code != null && Objects.equals(code, t.getCode())) {
            privateAccess.computeIfAbsent(tableId, k -> ConcurrentHashMap.newKeySet()).add(email);
            System.out.println("authorizeEmailForTable t=" + tableId + " email=" + email + " code=" + code + " -> authorized");
            return true;
        }
        System.out.println("authorizeEmailForTable t=" + tableId + " email=" + email + " code=" + code + " -> NOT authorized");
        return false;
    }

    // src/main/java/org/example/service/BjTableService.java
// Remplace la méthode existante `public synchronized void sit(String email, Long tableId, int seatIndex)`
// par la version ci-dessous.

    public synchronized void sit(String email, Long tableId, Integer seatIndex) {
        Long already = userTable.get(email);
        if (already != null && !already.equals(tableId)) {
            throw new IllegalStateException("Tu es déjà assis sur une autre table");
        }

        BjTable t = mustTable(tableId);
        markActive(t);

        // Si table privée, vérifier que l'email est autorisé (créateur ou a fourni le bon code précédemment)
        if (t.isPrivate()) {
            if (!Objects.equals(t.getCreatorEmail(), email)) {
                Set<String> allowed = privateAccess.get(tableId);
                if (allowed == null || !allowed.contains(email)) {
                    throw new IllegalStateException("Accès refusé : code requis pour cette table privée");
                }
            }
        }

        // Si seatIndex null : choisit le premier siège vide
        if (seatIndex == null) {
            Integer firstEmpty = null;
            for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
                Seat s = e.getValue();
                if (s == null || s.getStatus() == SeatStatus.EMPTY) {
                    firstEmpty = e.getKey();
                    break;
                }
            }
            if (firstEmpty == null) {
                throw new IllegalStateException("Aucun siège disponible");
            }
            seatIndex = firstEmpty;
        }

        Seat seat = t.getSeats().get(seatIndex);
        if (seat == null) throw new IllegalArgumentException("Seat invalide");
        if (seat.getStatus() != SeatStatus.EMPTY && !Objects.equals(seat.getEmail(), email))
            throw new IllegalStateException("Seat occupé");

        if (!t.isPrivate() && (t.getPhase() == TablePhase.PLAYING || t.getPhase() == TablePhase.DEALER_TURN)) {
            throw new IllegalStateException("Impossible de rejoindre une table publique en cours de main. Attends la prochaine main.");
        }

        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        seat.setUserId(u.getId());
        seat.setEmail(email);
        seat.setStatus(SeatStatus.SEATED);
        userTable.put(email, t.getId());
        cancelDisconnectTimer(email);

        if (t.getPhase() == TablePhase.BETTING) {
            goBetting(t);
        } else {
            broadcastState(t);
        }
        broadcastLobby();
    }


    public synchronized void bet(String email, BetMsg msg) {
        BjTable t = mustTable(msg.getTableId());
        markActive(t);
        if (t.getPhase() != TablePhase.BETTING) {
            throw new IllegalStateException("Hors phase BETTING");
        }

        Integer idx = msg.getSeatIndex();
        if (idx == null) {
            idx = findSeatIndexByEmail(t, email);
            if (idx == null) {
                throw new IllegalStateException("Aucun siège pour cet utilisateur");
            }
        }

        Seat seat = mustOwnedSeat(t, idx, email);

        long amount = msg.getAmount();
        if (amount <= 0) throw new IllegalArgumentException("Mise invalide");

        // VALIDATION min/max
        long min = t.getMinBet() != null ? t.getMinBet() : 0L;
        long max = t.getMaxBet() != null ? t.getMaxBet() : Long.MAX_VALUE;
        if (min > 0 && amount < min) {
            throw new IllegalArgumentException("Mise inférieure au minimum (" + min + ")");
        }
        if (max > 0 && max != Long.MAX_VALUE && amount > max) {
            throw new IllegalArgumentException("Mise supérieure au maximum (" + max + ")");
        }

        seat.getHand().setBet(amount);

        // On ne débite qu'au moment du lockBetsAndDeal
        broadcast(t, "BET_UPDATE", Map.of(
                "seat", idx,
                "bet", amount
        ));
    }

    private Integer findSeatIndexByEmail(BjTable t, String email) {
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            if (s != null && email.equals(s.getEmail())) {
                return e.getKey();
            }
        }
        return null;
    }

    public synchronized void action(String email, ActionMsg msg) {
        BjTable t = mustTable(msg.getTableId());
        if (t.getPhase() != TablePhase.PLAYING) throw new IllegalStateException("Hors phase PLAYING");
        if (!Objects.equals(t.getCurrentSeatIndex(), msg.getSeatIndex()))
            throw new IllegalStateException("Pas ton tour");

        Seat seat = mustOwnedSeat(t, msg.getSeatIndex(), email);

        switch (msg.getType()) {
            case HIT -> {
                seat.getHand().add(t.getShoe().draw());
                broadcast(t, "ACTION_RESULT", m("seat", msg.getSeatIndex(), "action", "HIT", "hand", seat.getHand()));
                if (seat.getHand().isBusted()) nextTurnOrDealer(t);
                else scheduleTurnTimeout(t);
            }
            case STAND -> {
                seat.getHand().setStanding(true);
                broadcast(t, "ACTION_RESULT", m("seat", msg.getSeatIndex(), "action", "STAND"));
                nextTurnOrDealer(t);
            }
            case DOUBLE -> {
                long add = seat.getHand().getBet();
                Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
                try {
                    walletService.debiter(u, add);
                } catch (Exception ex) {
                    broadcast(t, "ERROR", m("msg", "Solde insuffisant pour DOUBLE"));
                    return;
                }
                seat.getHand().setBet(seat.getHand().getBet() + add);
                seat.getHand().add(t.getShoe().draw());
                seat.getHand().setStanding(true);
                broadcast(t, "ACTION_RESULT", m("seat", msg.getSeatIndex(), "action", "DOUBLE", "hand", seat.getHand()));
                nextTurnOrDealer(t);
            }
        }
    }

    public synchronized void leave(String email, Long tableId, Integer seatIndex) {
        BjTable t = mustTable(tableId);
        markActive(t);
        if (seatIndex != null) {
            Seat seat = t.getSeats().get(seatIndex);
            if (seat != null && Objects.equals(seat.getEmail(), email)) {
                // si on est en cours de main on marque DISCONNECTED, sinon on libère le siège
                if (t.getPhase() == TablePhase.PLAYING || t.getPhase() == TablePhase.DEALER_TURN) {
                    seat.setStatus(SeatStatus.DISCONNECTED);
                    scheduleDisconnectCleanup(t, seatIndex, email);
                } else {
                    t.getSeats().put(seatIndex, new Seat());
                    userTable.remove(email);
                    cancelDisconnectTimer(email);
                }
            }
        }
        broadcastState(t);
        broadcastLobby();
    }

    public synchronized void markDisconnected(String email) {
        tables.values().forEach(table -> {
            table.getSeats().forEach((i, s) -> {
                if (email.equals(s.getEmail()) && s.getStatus() == SeatStatus.SEATED) {
                    s.setStatus(SeatStatus.DISCONNECTED);
                    scheduleDisconnectCleanup(table, i, email);
                }
            });
            broadcastState(table);
        });
        broadcastLobby();
    }

    public synchronized void markReconnected(String email) {
        // annule timer si existant et réassied si possible
        cancelDisconnectTimer(email);
        tables.values().forEach(table -> {
            table.getSeats().forEach((i, s) -> {
                if (email.equals(s.getEmail()) && s.getStatus() == SeatStatus.DISCONNECTED) {
                    s.setStatus(SeatStatus.SEATED);
                }
            });
            broadcastState(table);
        });
        broadcastLobby();
    }

    // ------------------------------------------------------------------------
    // LOGIQUE DE PHASES (inchangée sauf usage des nouvelles structures)
    // (les méthodes goBetting/lockBetsAndDeal/scheduleTurnTimeout/nextTurnOrDealer/dealerTurn/payouts
    // restent inchangées — je les conserve tels quels)

    private void goBetting(BjTable t) {
        t.setPhase(TablePhase.BETTING);
        t.setPhaseDeadlineEpochMs(Instant.now().toEpochMilli() + BETTING_MS);
        broadcastState(t);
        scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                try { lockBetsAndDeal(t); } catch (Exception ignored) {}
            }
        }, BETTING_MS, TimeUnit.MILLISECONDS);
    }

    private void lockBetsAndDeal(BjTable t) {
        boolean aDesMises = false;
        for (Map.Entry<Integer, Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            if (s.getStatus() != SeatStatus.EMPTY && s.getHand().getBet() > 0) {
                aDesMises = true;
                try {
                    Utilisateur u = utilisateurRepo.findByEmail(s.getEmail()).orElseThrow();
                    walletService.debiter(u, s.getHand().getBet());
                } catch (Exception ex) {
                    s.getHand().setBet(0);
                }
            }
        }
        if (!aDesMises) {
            t.setPhase(TablePhase.BETTING);
            broadcastState(t);
            goBetting(t);
            return;
        }

        t.getDealer().getCards().clear();
        t.getDealer().setStanding(false);
        t.getDealer().setBusted(false);
        t.getDealer().setBlackjack(false);

        for (Seat s : t.getSeats().values()) s.getHand().getCards().clear();

        for (int i = 0; i < 2; i++) {
            for (Seat s : t.getSeats().values()) {
                if (s.getHand().getBet() > 0) s.getHand().add(t.getShoe().draw());
            }
            t.getDealer().add(t.getShoe().draw());
        }

        t.setPhase(TablePhase.PLAYING);
        t.setCurrentSeatIndex(firstActiveSeat(t));
        scheduleTurnTimeout(t);

        broadcast(t, "HAND_START", m(
                "dealerUp", !t.getDealer().getCards().isEmpty() ? t.getDealer().getCards().get(0) : null,
                "deadline", t.getPhaseDeadlineEpochMs(),
                "players",  seatsPayload(t)
        ));

        if (t.getCurrentSeatIndex() == null) dealerTurn(t);
    }

    private void scheduleTurnTimeout(BjTable t) {
        Integer seatIdx = t.getCurrentSeatIndex();
        if (seatIdx == null) return;
        t.setPhaseDeadlineEpochMs(Instant.now().toEpochMilli() + TURN_MS);

        broadcast(t, "PLAYER_TURN", m("seat", seatIdx, "deadline", t.getPhaseDeadlineEpochMs()));

        scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                if (Objects.equals(seatIdx, t.getCurrentSeatIndex()) && t.getPhase() == TablePhase.PLAYING) {
                    Seat s = t.getSeats().get(seatIdx);
                    if (s != null && s.getHand().getBet() > 0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                        s.getHand().setStanding(true);
                        broadcast(t, "ACTION_RESULT", m("seat", seatIdx, "action", "AUTO_STAND"));
                        nextTurnOrDealer(t);
                    }
                }
            }
        }, TURN_MS, TimeUnit.MILLISECONDS);
    }

    private void nextTurnOrDealer(BjTable t) {
        Integer idx = t.getCurrentSeatIndex();
        if (idx == null) { dealerTurn(t); return; }

        int start = idx + 1, n = t.getMaxSeats();
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n;
            Seat s = t.getSeats().get(i);
            if (s != null && s.getHand().getBet() > 0 && !s.getHand().isBusted() && !s.getHand().isStanding()) {
                t.setCurrentSeatIndex(i);
                scheduleTurnTimeout(t);
                return;
            }
        }
        dealerTurn(t);
    }

    private void dealerTurn(BjTable t) {
        t.setPhase(TablePhase.DEALER_TURN);
        t.setCurrentSeatIndex(null);
        t.setPhaseDeadlineEpochMs(0L);
        broadcast(t, "DEALER_TURN_START", m("dealer", t.getDealer()));

        Runnable drawTask = new Runnable() {
            @Override
            public void run() {
                synchronized (BjTableService.this) {
                    if (t.getDealer().bestTotal() < 17) {
                        t.getDealer().add(t.getShoe().draw());
                        broadcast(t, "DEALER_TURN_UPDATE", m("dealer", t.getDealer()));
                        scheduler.schedule(this, 700, TimeUnit.MILLISECONDS);
                    } else {
                        broadcast(t, "DEALER_TURN_END", m("dealer", t.getDealer()));
                        payouts(t);
                    }
                }
            }
        };

        scheduler.schedule(drawTask, 700, TimeUnit.MILLISECONDS);
    }

    private void payouts(BjTable t) {
        int dealerTotal = t.getDealer().bestTotal();
        boolean dealerBust = dealerTotal > 21;
        List<Map<String, Object>> pay = new ArrayList<>();

        for (Map.Entry<Integer,Seat> e : t.getSeats().entrySet()) {
            Seat s = e.getValue();
            long bet = s.getHand().getBet();
            if (bet <= 0) continue;

            long credit = 0;
            int total = s.getHand().bestTotal();
            String outcome;

            if (s.getHand().isBusted()) {
                credit = 0;
                outcome = "LOSE";
            } else if (s.getHand().isBlackjack() && t.getDealer().isBlackjack()) {
                credit = bet;
                outcome = "PUSH";
            } else if (s.getHand().isBlackjack()) {
                credit = bet + (bet * 3) / 2;
                outcome = "BLACKJACK";
            } else if (t.getDealer().isBlackjack()) {
                credit = 0;
                outcome = "LOSE";
            } else if (dealerBust || total > dealerTotal) {
                credit = bet * 2;
                outcome = "WIN";
            } else if (total == dealerTotal) {
                credit = bet;
                outcome = "PUSH";
            } else {
                credit = 0;
                outcome = "LOSE";
            }

            if (credit > 0) {
                Utilisateur u = utilisateurRepo.findByEmail(s.getEmail()).orElseThrow();
                walletService.crediter(u, credit);
            }

            // ✅ Enregistrement historique
            try {
                Utilisateur u = utilisateurRepo.findByEmail(s.getEmail()).orElseThrow();
                int multiplier = switch (outcome) {
                    case "BLACKJACK" -> 3;
                    case "WIN" -> 2;
                    case "PUSH" -> 1;
                    default -> 0;
                };
                historyService.record(
                        u,
                        "blackjack",
                        "total=" + total + ",outcome=" + outcome,
                        bet,
                        credit,
                        multiplier
                );
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            pay.add(Map.of(
                    "seat", e.getKey(),
                    "bet", bet,
                    "credit", credit,
                    "total", total,
                    "outcome", outcome
            ));
        }

        t.setPhase(TablePhase.PAYOUT);
        t.setPhaseDeadlineEpochMs(Instant.now().toEpochMilli() + RESULT_MS);
        broadcast(t, "PAYOUTS", Map.of("payouts", pay));
        broadcastState(t);

        scheduler.schedule(() -> {
            synchronized (BjTableService.this) {
                for (Seat s : t.getSeats().values()) s.resetForNextHand();
                t.getDealer().getCards().clear();
                t.setPhase(TablePhase.BETTING);
                t.setPhaseDeadlineEpochMs(Instant.now().toEpochMilli() + BETTING_MS);
                broadcastState(t);
                goBetting(t);
            }
        }, RESULT_MS, TimeUnit.MILLISECONDS);
    }

    private void doCloseNow(Long tableId, BjTable t) {

        broadcast(t, "TABLE_CLOSED", Map.of(
                "msg", "La table a été fermée par le créateur"
        ));

        for (Seat s : t.getSeats().values()) {
            if (s.getEmail() != null) {
                userTable.remove(s.getEmail());
                cancelDisconnectTimer(s.getEmail());
            }
        }
        tables.remove(tableId);
        privateAccess.remove(tableId);

        try { bjTableRepository.deleteById(tableId); } catch (Exception ignored) {}

        broadcastLobby();
    }



    // ------------------------------------------------------------------------
    // Admin / close table
    public synchronized void closeTable(Long tableId, String requesterEmail) {
        BjTable t = mustTable(tableId);
        boolean allowed = Objects.equals(t.getCreatorEmail(), requesterEmail);
        if (!allowed) {
            Utilisateur u = utilisateurRepo.findByEmail(requesterEmail).orElse(null);
            if (u != null && "ADMIN".equalsIgnoreCase(u.getRole())) allowed = true;
        }
        if (!allowed) throw new IllegalStateException("Seul le créateur ou un ADMIN peut fermer la table");

        // 🚨 Cas spécial : si on est en phase BETTING → on annule tout
        if (t.getPhase() == TablePhase.BETTING) {
            // on réinitialise les sièges (kick tout le monde)
            for (Seat s : t.getSeats().values()) {
                if (s.getEmail() != null) {
                    userTable.remove(s.getEmail());
                    cancelDisconnectTimer(s.getEmail());
                }
            }
            t.getSeats().clear(); // reset complet
            doCloseNow(tableId, t); // fermeture immédiate
            return;
        }

        // ⚠️ Si une manche est en cours → on attend la fin (comme avant)
        if (t.getPhase() == TablePhase.PLAYING
                || t.getPhase() == TablePhase.DEALER_TURN
                || t.getPhase() == TablePhase.PAYOUT) {
            t.setPendingClose(true);
            return;
        }

        // Sinon fermeture immédiate
        doCloseNow(tableId, t);
    }

    @Scheduled(fixedRate = 600_000) // toutes les 60 secondes
    public synchronized void nettoyerTablesInactives() {
        Instant now = Instant.now();
        List<Long> toRemove = new ArrayList<>();

        for (BjTable t : tables.values()) {
            boolean vide = t.getSeats().values().stream()
                    .allMatch(s -> s == null || s.getStatus() == SeatStatus.EMPTY);

            // Si table vide depuis plus d'une minute
            if (vide && Duration.between(t.getLastActiveAt(), now).toMillis() > 60_000) {
                toRemove.add(t.getId());
            }
        }

        for (Long id : toRemove) {
            BjTable t = tables.get(id);
            if (t != null) {
                System.out.println("🧹 Suppression automatique de la table " + id + " (inactive depuis > 1min)");
                doCloseNow(id, t);
            }
        }
    }


}
