package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service // Indique que c’est un service Spring (logique métier)
public class WalletService {
    @Autowired // Injection automatique du repository (accès BDD)
    private WalletRepository walletRepo;

    @Autowired // Injection du service SSE (notifications temps réel)
    private WalletSseService walletSseService;

    // Récupère le wallet d’un utilisateur, ou le crée s’il n’existe pas
    public Wallet getWalletParUtilisateur(Utilisateur u){
        return walletRepo.findByUtilisateur(u).orElseGet(() -> {
            // si aucun wallet trouvé → on en crée un
            Wallet w = Wallet.builder()
                    .utilisateur(u) // lien avec l’utilisateur
                    .solde(0L)      // solde initial à 0
                    .build();
            return walletRepo.save(w); // sauvegarde en BDD
        });
    }

    @Transactional // garantit que toutes les opérations sont atomiques
    public Wallet crediter(Utilisateur u, long montant){
        // Vérifie ou crée le wallet
        getWalletParUtilisateur(u);

        // Incrémente le solde via une requête UPDATE
        int updated = walletRepo.incrementSolde(u, montant);

        // Recharge le wallet depuis la BDD pour avoir la valeur à jour
        Wallet w = walletRepo.findByUtilisateur(u).orElseThrow();

        // Notifie en temps réel (SSE) l’utilisateur de son nouveau solde
        walletSseService.sendBalanceUpdate(u.getEmail(), w.getSolde());

        return w; // renvoie le wallet mis à jour
    }

    @Transactional
    public Wallet debiter(Utilisateur u, long montant){
        // Vérifie ou crée le wallet
        getWalletParUtilisateur(u);

        // Tente de décrémenter le solde uniquement si suffisant
        int updated = walletRepo.decrementSoldeIfEnough(u, montant);

        // Si aucun update → solde insuffisant → exception
        if(updated == 0) throw new IllegalArgumentException("Solde insuffisant");

        // Recharge le wallet depuis la BDD
        Wallet w = walletRepo.findByUtilisateur(u).orElseThrow();

        // Notifie en temps réel du nouveau solde
        walletSseService.sendBalanceUpdate(u.getEmail(), w.getSolde());

        return w;
    }
}
