package org.example.service;

import org.example.dto.BonusStatusDTO;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BonusServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepo;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private BonusService bonusService;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setPseudo("User");
        return u;
    }

    // -------- getStatus() --------

    @Test
    void getStatus_shouldAllowClaim_whenNeverClaimed() {
        Utilisateur u = buildUser();
        u.setLastBonusClaim(null);

        BonusStatusDTO status = bonusService.getStatus(u);

        assertThat(status.isCanClaim()).isTrue();
        assertThat(status.getAmount()).isEqualTo(1000L);
        assertThat(status.getLastClaimEpochMs()).isNull();
        assertThat(status.getServerNowEpochMs()).isNotNull();
        assertThat(status.getNextResetEpochMs()).isNotNull();
    }

    @Test
    void getStatus_shouldForbidClaim_whenLastClaimIsAfterReset() {
        Utilisateur u = buildUser();
        // future => forcément après le dernier reset
        Instant future = Instant.now().plusSeconds(3600);
        u.setLastBonusClaim(future);

        BonusStatusDTO status = bonusService.getStatus(u);

        assertThat(status.isCanClaim()).isFalse();
        assertThat(status.getLastClaimEpochMs()).isEqualTo(future.toEpochMilli());
        assertThat(status.getNextResetEpochMs())
                .isGreaterThanOrEqualTo(status.getServerNowEpochMs());
    }

    // -------- claimDailyBonus() --------

    @Test
    void claimDailyBonus_shouldCreditWalletAndUpdateLastClaim_andSaveUser() {
        Utilisateur u = buildUser();
        // très ancien => toujours avant le dernier reset
        u.setLastBonusClaim(Instant.EPOCH);

        Wallet w = new Wallet();
        w.setSolde(2000L);

        when(walletService.crediter(u, 1000L)).thenReturn(w);
        when(utilisateurRepo.save(any(Utilisateur.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet result = bonusService.claimDailyBonus(u);

        assertThat(result).isSameAs(w);
        assertThat(u.getLastBonusClaim()).isNotNull();

        verify(walletService).crediter(u, 1000L);
        verify(utilisateurRepo).save(u);
    }

    @Test
    void claimDailyBonus_shouldThrow_whenAlreadyClaimedAfterReset() {
        Utilisateur u = buildUser();
        // future => considéré comme déjà réclamé après le dernier reset
        u.setLastBonusClaim(Instant.now().plusSeconds(3600));

        assertThrows(IllegalStateException.class,
                () -> bonusService.claimDailyBonus(u));

        verifyNoInteractions(walletService);
        verify(utilisateurRepo, never()).save(any());
    }
}
