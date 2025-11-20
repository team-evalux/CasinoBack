package org.example.controller;

import org.example.dto.BonusStatusDTO;
import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.BonusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BonusControllerTest {

    @Mock
    private BonusService bonusService;

    @Mock
    private UtilisateurRepository utilisateurRepo;

    @InjectMocks
    private BonusController bonusController;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setPseudo("User");
        return u;
    }

    private Authentication authFor(String email) {
        return new UsernamePasswordAuthenticationToken(email, null);
    }

    @Test
    void status_shouldReturnBonusStatusForAuthenticatedUser() {
        Utilisateur u = buildUser();
        BonusStatusDTO dto = BonusStatusDTO.builder()
                .canClaim(true)
                .amount(1000L)
                .build();

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(bonusService.getStatus(u)).thenReturn(dto);

        Authentication auth = authFor("user@example.com");

        ResponseEntity<BonusStatusDTO> response = bonusController.status(auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(dto);
        verify(utilisateurRepo).findByEmail("user@example.com");
        verify(bonusService).getStatus(u);
    }

    @Test
    void claim_shouldReturnOkAndUpdatedStatus_whenBonusCanBeClaimed() {
        Utilisateur u = buildUser();
        Wallet w = new Wallet();
        w.setSolde(3000L);

        BonusStatusDTO status = BonusStatusDTO.builder()
                .canClaim(false)
                .amount(1000L)
                .serverNowEpochMs(System.currentTimeMillis())
                .nextResetEpochMs(System.currentTimeMillis() + 1000)
                .build();

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(bonusService.claimDailyBonus(u)).thenReturn(w);
        when(bonusService.getStatus(u)).thenReturn(status);

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = bonusController.claim(auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(BonusStatusDTO.class);

        BonusStatusDTO body = (BonusStatusDTO) response.getBody();
        assertThat(body.getSolde()).isEqualTo(3000L);

        verify(bonusService).claimDailyBonus(u);
        verify(bonusService).getStatus(u);
    }

    @Test
    void claim_shouldReturnBadRequest_whenBonusAlreadyClaimed() {
        Utilisateur u = buildUser();

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(bonusService.claimDailyBonus(u))
                .thenThrow(new IllegalStateException("Bonus déjà réclamé pour aujourd'hui."));

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = bonusController.claim(auth);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getBody();
        assertThat(body.get("error")).contains("Bonus déjà réclamé");

        verify(bonusService).claimDailyBonus(u);
    }
}
