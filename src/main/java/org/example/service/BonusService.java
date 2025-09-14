// src/main/java/org/example/service/BonusService.java
package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;

@Service
public class BonusService {

    private static final ZoneId PARIS_TZ = ZoneId.of("Europe/Paris");
    private static final int RESET_HOUR = 21;
    private static final int RESET_MIN = 30;
    private static final long BONUS_AMOUNT = 1000L;

    @Autowired
    private UtilisateurRepository utilisateurRepo;

    @Autowired
    private WalletService walletService;

    public Wallet claimDailyBonus(Utilisateur u) {
        Instant now = Instant.now();
        Instant lastReset = getLastResetInstant();

        if (u.getLastBonusClaim() != null && u.getLastBonusClaim().isAfter(lastReset)) {
            throw new IllegalStateException("Bonus déjà réclamé pour aujourd'hui.");
        }

        // crédite le compte
        Wallet w = walletService.crediter(u, BONUS_AMOUNT);

        // enregistre la date du claim
        u.setLastBonusClaim(now);
        utilisateurRepo.save(u);

        return w;
    }

    private Instant getLastResetInstant() {
        ZonedDateTime parisNow = ZonedDateTime.now(PARIS_TZ);

        ZonedDateTime resetToday = parisNow.withHour(RESET_HOUR).withMinute(RESET_MIN).withSecond(0).withNano(0);

        if (parisNow.isBefore(resetToday)) {
            resetToday = resetToday.minusDays(1);
        }

        return resetToday.toInstant();
    }
}
