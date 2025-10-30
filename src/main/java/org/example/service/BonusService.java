package org.example.service;

import org.example.dto.BonusStatusDTO;
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

    // --- NOUVEAU : statut courant ---
    public BonusStatusDTO getStatus(Utilisateur u) {
        Instant now = Instant.now();
        Instant lastReset = getLastResetInstant();
        boolean canClaim = (u.getLastBonusClaim() == null) || u.getLastBonusClaim().isBefore(lastReset);
        Instant nextReset = lastReset.plus(Duration.ofDays(1));

        return BonusStatusDTO.builder()
                .canClaim(canClaim)
                .lastClaimEpochMs(u.getLastBonusClaim() != null ? u.getLastBonusClaim().toEpochMilli() : null)
                .nextResetEpochMs(nextReset.toEpochMilli())
                .serverNowEpochMs(now.toEpochMilli())
                .amount(BONUS_AMOUNT)
                .build();
    }

    public Wallet claimDailyBonus(Utilisateur u) {
        Instant now = Instant.now();
        Instant lastReset = getLastResetInstant();

        if (u.getLastBonusClaim() != null && u.getLastBonusClaim().isAfter(lastReset)) {
            throw new IllegalStateException("Bonus déjà réclamé pour aujourd'hui.");
        }

        Wallet w = walletService.crediter(u, BONUS_AMOUNT);
        u.setLastBonusClaim(now);
        utilisateurRepo.save(u);
        return w;
    }

    // reset quotidien (Paris) -> dernier reset effectif
    private Instant getLastResetInstant() {
        ZonedDateTime parisNow = ZonedDateTime.now(PARIS_TZ);
        ZonedDateTime resetToday = parisNow.withHour(RESET_HOUR).withMinute(RESET_MIN).withSecond(0).withNano(0);
        if (parisNow.isBefore(resetToday)) {
            resetToday = resetToday.minusDays(1);
        }
        return resetToday.toInstant();
    }
}
