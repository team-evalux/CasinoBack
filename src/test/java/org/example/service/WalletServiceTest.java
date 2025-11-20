package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
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
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepo;

    @Mock
    private WalletSseService walletSseService;

    // UtilisateurRepository est @Autowired dans le service mais jamais utilisé ici,
    // donc on ne le moque même pas.

    @InjectMocks
    private WalletService walletService;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        return u;
    }

    // --- getWalletParUtilisateur() ---

    @Test
    void getWalletParUtilisateur_shouldReturnExistingWallet_ifAlreadyExists() {
        Utilisateur u = buildUser();
        Wallet existing = Wallet.builder()
                .id(10L)
                .utilisateur(u)
                .solde(1000L)
                .build();

        when(walletRepo.findByUtilisateur(u)).thenReturn(Optional.of(existing));

        Wallet result = walletService.getWalletParUtilisateur(u);

        assertThat(result).isSameAs(existing);
        verify(walletRepo).findByUtilisateur(u);
        verify(walletRepo, never()).save(any(Wallet.class));
    }

    @Test
    void getWalletParUtilisateur_shouldCreateAndSaveWallet_ifNotExists() {
        Utilisateur u = buildUser();

        when(walletRepo.findByUtilisateur(u)).thenReturn(Optional.empty());

        // Simuler la save() qui assigne un id
        when(walletRepo.save(any(Wallet.class))).thenAnswer(invocation -> {
            Wallet w = invocation.getArgument(0);
            w.setId(42L);
            return w;
        });

        Wallet result = walletService.getWalletParUtilisateur(u);

        // Vérifier que save() a été appelé avec un wallet pour cet utilisateur
        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepo).save(captor.capture());
        Wallet saved = captor.getValue();

        assertThat(saved.getUtilisateur()).isEqualTo(u);
        // dans ton builder tu mets solde(0L), donc on s’attend à 0L
        assertThat(saved.getSolde()).isEqualTo(0L);

        // Le résultat est bien le wallet sauvegardé
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getUtilisateur()).isEqualTo(u);
    }

    // --- crediter() ---

    @Test
    void crediter_shouldIncrementBalanceAndSendSse() {
        Utilisateur u = buildUser();

        // Wallet avant mise à jour
        Wallet before = Wallet.builder()
                .id(10L)
                .utilisateur(u)
                .solde(1000L)
                .build();

        // Wallet après mise à jour (comme si la DB avait appliqué l’update)
        Wallet after = Wallet.builder()
                .id(10L)
                .utilisateur(u)
                .solde(1200L)
                .build();

        // 1ère fois : appelée dans getWalletParUtilisateur()
        // 2ème fois : appelée après incrementSolde()
        when(walletRepo.findByUtilisateur(u))
                .thenReturn(Optional.of(before), Optional.of(after));

        when(walletRepo.incrementSolde(u, 200L)).thenReturn(1);

        Wallet result = walletService.crediter(u, 200L);

        // Vérifier les appels
        verify(walletRepo).incrementSolde(u, 200L);
        verify(walletSseService).sendBalanceUpdate("user@example.com", 1200L);

        assertThat(result.getSolde()).isEqualTo(1200L);
    }

    // --- debiter() ---

    @Test
    void debiter_shouldDecrementBalanceAndSendSse_whenEnoughSolde() {
        Utilisateur u = buildUser();

        Wallet before = Wallet.builder()
                .id(10L)
                .utilisateur(u)
                .solde(1000L)
                .build();

        Wallet after = Wallet.builder()
                .id(10L)
                .utilisateur(u)
                .solde(800L)
                .build();

        when(walletRepo.findByUtilisateur(u))
                .thenReturn(Optional.of(before), Optional.of(after));

        when(walletRepo.decrementSoldeIfEnough(u, 200L)).thenReturn(1);

        Wallet result = walletService.debiter(u, 200L);

        verify(walletRepo).decrementSoldeIfEnough(u, 200L);
        verify(walletSseService).sendBalanceUpdate("user@example.com", 800L);

        assertThat(result.getSolde()).isEqualTo(800L);
    }

    @Test
    void debiter_shouldThrowException_whenSoldeInsuffisant() {
        Utilisateur u = buildUser();

        Wallet existing = Wallet.builder()
                .id(10L)
                .utilisateur(u)
                .solde(100L)
                .build();

        when(walletRepo.findByUtilisateur(u)).thenReturn(Optional.of(existing));
        when(walletRepo.decrementSoldeIfEnough(u, 200L)).thenReturn(0); // update échoue

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> walletService.debiter(u, 200L)
        );

        assertThat(ex.getMessage()).isEqualTo("Solde insuffisant");
        verify(walletSseService, never()).sendBalanceUpdate(anyString(), anyLong());
    }

    // --- supprimerWallet() ---

    @Test
    void supprimerWallet_shouldCompleteSseAndDeleteWallet() {
        Utilisateur u = buildUser();

        walletService.supprimerWallet(u);

        verify(walletSseService).complete("user@example.com");
        verify(walletRepo).deleteByUtilisateur(u);
    }
}
