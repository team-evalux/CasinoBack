package org.example.controller;

import org.example.dto.AuthRequest;
import org.example.dto.AuthResponse;
import org.example.model.Utilisateur;
import org.example.security.JwtUtil;
import org.example.service.UtilisateurService;
import org.example.service.VerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerLoginTest {

    @Mock
    private UtilisateurService utilisateurService;

    @Mock
    private VerificationService verificationService; // juste pour le constructeur

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setPseudo("User");
        u.setRole("USER");
        u.setMotDePasseHash("hashed");
        return u;
    }

    @Test
    void login_shouldReturn401_whenUserNotFound() {
        AuthRequest req = new AuthRequest();
        req.setEmail("inconnu@example.com");
        req.setMotDePasse("pwd");

        when(utilisateurService.trouverParEmail("inconnu@example.com"))
                .thenReturn(null);

        ResponseEntity<?> response = authController.login(req);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo("Identifiants invalides");

        verify(utilisateurService).trouverParEmail("inconnu@example.com");
        verify(utilisateurService, never()).verifierMotDePasse(any(), anyString());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void login_shouldReturn401_whenPasswordInvalid() {
        Utilisateur u = buildUser();
        AuthRequest req = new AuthRequest();
        req.setEmail("user@example.com");
        req.setMotDePasse("wrong");

        when(utilisateurService.trouverParEmail("user@example.com"))
                .thenReturn(u);
        when(utilisateurService.verifierMotDePasse(u, "wrong"))
                .thenReturn(false);

        ResponseEntity<?> response = authController.login(req);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo("Identifiants invalides");

        verify(utilisateurService).trouverParEmail("user@example.com");
        verify(utilisateurService).verifierMotDePasse(u, "wrong");
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void login_shouldReturnAuthResponse_whenCredentialsAreValid() {
        Utilisateur u = buildUser();
        AuthRequest req = new AuthRequest();
        req.setEmail("user@example.com");
        req.setMotDePasse("secret");

        when(utilisateurService.trouverParEmail("user@example.com"))
                .thenReturn(u);
        when(utilisateurService.verifierMotDePasse(u, "secret"))
                .thenReturn(true);
        when(jwtUtil.genererToken("user@example.com"))
                .thenReturn("jwt-token");

        ResponseEntity<?> response = authController.login(req);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(AuthResponse.class);

        AuthResponse body = (AuthResponse) response.getBody();
        // accès direct aux champs publics
        assertThat(body.getToken()).isEqualTo("jwt-token");
        assertThat(body.getEmail()).isEqualTo("user@example.com");
        assertThat(body.getPseudo()).isEqualTo("User");
        assertThat(body.getRole()).isEqualTo("USER");

        verify(utilisateurService).trouverParEmail("user@example.com");
        verify(utilisateurService).verifierMotDePasse(u, "secret");
        verify(jwtUtil).genererToken("user@example.com");
    }
}
