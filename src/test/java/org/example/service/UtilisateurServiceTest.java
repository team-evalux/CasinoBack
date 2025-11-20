package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.repo.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UtilisateurServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepo;

    @Mock
    private WalletRepository walletRepo;

    @InjectMocks
    private UtilisateurService utilisateurService;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setPseudo("User");
        u.setMotDePasseHash("hash");
        u.setRole("USER");
        return u;
    }

    // --- inscrire() ---

    @Test
    void inscrire_shouldSaveUserWithEncodedPassword_andCreateInitialWallet() {
        // ARRANGE
        // On simule le save : on renvoie l'utilisateur avec un id
        when(utilisateurRepo.save(any(Utilisateur.class))).thenAnswer(invocation -> {
            Utilisateur u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        // On peut simplement renvoyer le wallet tel quel
        when(walletRepo.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // ACT
        String email = "test@example.com";
        String pseudo = "TestUser";
        String motDePasse = "Password123!";
        Utilisateur saved = utilisateurService.inscrire(email, pseudo, motDePasse);

        // ASSERT : côté utilisateur
        ArgumentCaptor<Utilisateur> userCaptor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepo).save(userCaptor.capture());
        Utilisateur uToSave = userCaptor.getValue();

        assertThat(uToSave.getEmail()).isEqualTo(email);
        assertThat(uToSave.getPseudo()).isEqualTo(pseudo);
        assertThat(uToSave.getMotDePasseHash()).isNotEqualTo(motDePasse); // mot de passe encodé
        assertThat(uToSave.getRole()).isEqualTo("USER");
        assertThat(uToSave.isActive()).isTrue();
        assertThat(uToSave.getDateCreation()).isNotNull();

        // l'objet retourné par le service est bien celui sauvegardé
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getEmail()).isEqualTo(email);

        // ASSERT : côté wallet initial
        ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepo).save(walletCaptor.capture());
        Wallet wToSave = walletCaptor.getValue();

        assertThat(wToSave.getUtilisateur()).isEqualTo(saved);
        assertThat(wToSave.getSolde()).isEqualTo(1000L);
    }

    // --- trouverParEmail() ---

    @Test
    void trouverParEmail_shouldReturnUser_whenFound() {
        Utilisateur u = buildUser();
        when(utilisateurRepo.findByEmail("user@example.com")).thenReturn(Optional.of(u));

        Utilisateur result = utilisateurService.trouverParEmail("user@example.com");

        assertThat(result).isSameAs(u);
    }

    @Test
    void trouverParEmail_shouldReturnNull_whenNotFound() {
        when(utilisateurRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Utilisateur result = utilisateurService.trouverParEmail("unknown@example.com");

        assertThat(result).isNull();
    }

    // --- trouverParPseudo() ---

    @Test
    void trouverParPseudo_shouldReturnUser_whenFound() {
        Utilisateur u = buildUser();
        when(utilisateurRepo.findByPseudo("User")).thenReturn(Optional.of(u));

        Utilisateur result = utilisateurService.trouverParPseudo("User");

        assertThat(result).isSameAs(u);
    }

    @Test
    void trouverParPseudo_shouldReturnNull_whenNotFound() {
        when(utilisateurRepo.findByPseudo("Ghost")).thenReturn(Optional.empty());

        Utilisateur result = utilisateurService.trouverParPseudo("Ghost");

        assertThat(result).isNull();
    }

    // --- verifierMotDePasse() ---

    @Test
    void verifierMotDePasse_shouldReturnTrueForCorrectPassword_andFalseOtherwise() {
        // On utilise le service lui-même pour encoder en passant par inscrire()
        when(utilisateurRepo.save(any(Utilisateur.class))).thenAnswer(invocation -> {
            Utilisateur u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(walletRepo.save(any(Wallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Utilisateur u = utilisateurService.inscrire("test@example.com", "TestUser", "Secret123");

        boolean ok = utilisateurService.verifierMotDePasse(u, "Secret123");
        boolean ko = utilisateurService.verifierMotDePasse(u, "MauvaisMotDePasse");

        assertThat(ok).isTrue();
        assertThat(ko).isFalse();
    }

    // --- mettreAJourMotDePasse() ---

    @Test
    void mettreAJourMotDePasse_shouldEncodeAndSaveNewPassword_whenUserExists() {
        Utilisateur u = buildUser();
        u.setMotDePasseHash("oldHash");

        when(utilisateurRepo.findByEmail("user@example.com")).thenReturn(Optional.of(u));
        when(utilisateurRepo.save(any(Utilisateur.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String nouveau = "NewPassword123!";
        utilisateurService.mettreAJourMotDePasse("user@example.com", nouveau);

        ArgumentCaptor<Utilisateur> userCaptor = ArgumentCaptor.forClass(Utilisateur.class);
        verify(utilisateurRepo).save(userCaptor.capture());
        Utilisateur saved = userCaptor.getValue();

        assertThat(saved.getMotDePasseHash()).isNotEqualTo("oldHash");
        // on vérifie que le nouveau mot de passe est bien accepté par le service
        assertThat(utilisateurService.verifierMotDePasse(saved, nouveau)).isTrue();
    }

    @Test
    void mettreAJourMotDePasse_shouldThrow_whenUserNotFound() {
        when(utilisateurRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> utilisateurService.mettreAJourMotDePasse("unknown@example.com", "whatever")
        );

        assertThat(ex.getMessage()).isEqualTo("Utilisateur introuvable");
        verify(utilisateurRepo, never()).save(any(Utilisateur.class));
    }
}
