package org.example.controller;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.example.service.WalletSseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;


import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private UtilisateurRepository utilisateurRepo;

    @Mock
    private WalletSseService walletSseService;

    @InjectMocks
    private WalletController walletController;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setPseudo("User");
        return u;
    }

    private Wallet buildWallet(Utilisateur u, long solde) {
        Wallet w = new Wallet();
        w.setId(10L);
        w.setUtilisateur(u);
        w.setSolde(solde);
        return w;
    }

    private Authentication authFor(String email) {
        // Le 3e paramètre = authorities -> token authentifié
        return new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }


    @Test
    void solde_shouldReturnWalletForAuthenticatedUser() {
        Utilisateur u = buildUser();
        Wallet w = buildWallet(u, 1500L);

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(walletService.getWalletParUtilisateur(u))
                .thenReturn(w);

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = walletController.solde(auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(w);

        verify(utilisateurRepo).findByEmail("user@example.com");
        verify(walletService).getWalletParUtilisateur(u);
    }

    @Test
    void crediter_shouldCallServiceAndReturnWallet() {
        Utilisateur u = buildUser();
        Wallet w = buildWallet(u, 2000L);

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(walletService.crediter(u, 500L))
                .thenReturn(w);

        Authentication auth = authFor("user@example.com");
        Map<String, Long> body = Map.of("montant", 500L);

        ResponseEntity<?> response = walletController.crediter(body, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(w);

        verify(walletService).crediter(u, 500L);
    }

    @Test
    void crediter_shouldUseZeroWhenMontantMissing() {
        Utilisateur u = buildUser();
        Wallet w = buildWallet(u, 1000L);

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(walletService.crediter(u, 0L))
                .thenReturn(w);

        Authentication auth = authFor("user@example.com");
        Map<String, Long> body = Map.of(); // pas de montant

        ResponseEntity<?> response = walletController.crediter(body, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(w);

        verify(walletService).crediter(u, 0L);
    }

    @Test
    void debiter_shouldReturnWalletWhenEnoughSolde() {
        Utilisateur u = buildUser();
        Wallet w = buildWallet(u, 500L);

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(walletService.debiter(u, 300L))
                .thenReturn(w);

        Authentication auth = authFor("user@example.com");
        Map<String, Long> body = Map.of("montant", 300L);

        ResponseEntity<?> response = walletController.debiter(body, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(w);

        verify(walletService).debiter(u, 300L);
    }

    @Test
    void debiter_shouldReturnBadRequestWhenInsufficientSolde() {
        Utilisateur u = buildUser();

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(walletService.debiter(u, 1000L))
                .thenThrow(new IllegalArgumentException("Solde insuffisant"));

        Authentication auth = authFor("user@example.com");
        Map<String, Long> body = Map.of("montant", 1000L);

        ResponseEntity<?> response = walletController.debiter(body, auth);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, String> error = (Map<String, String>) response.getBody();
        assertThat(error.get("error")).isEqualTo("Solde insuffisant");

        verify(walletService).debiter(u, 1000L);
    }

    @Test
    void stream_shouldReturnEmitterWhenAuthenticated() {
        Authentication auth = authFor("user@example.com");
        SseEmitter emitter = new SseEmitter();

        when(walletSseService.register("user@example.com"))
                .thenReturn(emitter);

        SseEmitter result = walletController.stream(auth);

        assertThat(result).isSameAs(emitter);
        verify(walletSseService).register("user@example.com");
    }

    @Test
    void stream_shouldThrowUnauthorizedWhenNoAuth() {
        assertThatThrownBy(() -> walletController.stream(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void supprimerMonWallet_shouldDeleteAndReturnNoContent() {
        Utilisateur u = buildUser();

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = walletController.supprimerMonWallet(auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        verify(walletService).supprimerWallet(u);
    }
}
