// src/main/java/org/example/service/BonusService.java
package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;

@Service // Bean Spring : service métier pour la gestion du bonus quotidien
public class BonusService {

    // Fuseau utilisé pour le reset quotidien
    private static final ZoneId PARIS_TZ = ZoneId.of("Europe/Paris");
    // Heure/minute de reset du bonus quotidien (ici 21:30 Europe/Paris)
    private static final int RESET_HOUR = 21;
    private static final int RESET_MIN = 30;
    // Montant du bonus crédité lors d'un claim valide
    private static final long BONUS_AMOUNT = 1000L;

    @Autowired // Accès aux utilisateurs pour enregistrer la date de dernier claim
    private UtilisateurRepository utilisateurRepo;

    @Autowired // Service portefeuille : effectue le crédit et notifie via SSE
    private WalletService walletService;

    /**
     * Tente de "claimer" le bonus quotidien pour l'utilisateur 'u'.
     * Règle : un seul bonus par fenêtre quotidienne, définie par le reset à 21:30 Europe/Paris.
     * - Si l'utilisateur a déjà réclamé après le dernier reset : exception (déjà pris).
     * - Sinon : crédite le wallet de BONUS_AMOUNT, sauvegarde la date de claim (Instant.now), et renvoie le Wallet à jour.
     */
    public Wallet claimDailyBonus(Utilisateur u) {
        Instant now = Instant.now();              // moment courant (UTC)
        Instant lastReset = getLastResetInstant(); // instant du dernier reset selon le fuseau/heure définis

        // Si le dernier claim existe ET est postérieur au dernier reset → bonus déjà pris pour cette fenêtre
        if (u.getLastBonusClaim() != null && u.getLastBonusClaim().isAfter(lastReset)) {
            throw new IllegalStateException("Bonus déjà réclamé pour aujourd'hui.");
        }

        // Créditer le compte de l'utilisateur (émet SSE via WalletSseService)
        Wallet w = walletService.crediter(u, BONUS_AMOUNT);

        // Enregistrer la date du claim (UTC) sur l'utilisateur et persister
        u.setLastBonusClaim(now);
        utilisateurRepo.save(u);

        return w; // retourne l'état du wallet après crédit
    }

    /**
     * Calcule l'Instant représentant le "dernier reset" (21:30 Europe/Paris).
     * - Prend l'heure locale à Paris.
     * - Construit l'horodatage du reset "d'aujourd'hui" à 21:30.
     * - Si l'heure actuelle est AVANT ce reset, alors le dernier reset est celui de la veille.
     * - Retourne l'Instant UTC correspondant.
     */
    private Instant getLastResetInstant() {
        ZonedDateTime parisNow = ZonedDateTime.now(PARIS_TZ); // date/heure courantes à Paris

        // Reset "théorique" du jour à 21:30:00
        ZonedDateTime resetToday = parisNow.withHour(RESET_HOUR).withMinute(RESET_MIN).withSecond(0).withNano(0);

        // Si on n'a pas encore atteint l'heure de reset aujourd'hui, alors le "dernier reset" est hier 21:30
        if (parisNow.isBefore(resetToday)) {
            resetToday = resetToday.minusDays(1);
        }

        return resetToday.toInstant(); // conversion en Instant (UTC)
    }
}
