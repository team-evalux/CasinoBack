package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    @Autowired
    private WalletRepository walletRepo;

    @Autowired
    private WalletSseService walletSseService;

    public Wallet getWalletParUtilisateur(Utilisateur u){
        return walletRepo.findByUtilisateur(u).orElseGet(() -> {
            Wallet w = Wallet.builder()
                    .utilisateur(u)
                    .solde(0L)
                    .build();
            return walletRepo.save(w);
        });
    }

    @Transactional
    public Wallet crediter(Utilisateur u, long montant){
        // ensure wallet exists
        getWalletParUtilisateur(u);
        int updated = walletRepo.incrementSolde(u, montant);
        Wallet w = walletRepo.findByUtilisateur(u).orElseThrow();
        // notifier via SSE
        walletSseService.sendBalanceUpdate(u.getEmail(), w.getSolde());
        return w;
    }

    @Transactional
    public Wallet debiter(Utilisateur u, long montant){
        // ensure wallet exists
        getWalletParUtilisateur(u);
        int updated = walletRepo.decrementSoldeIfEnough(u, montant);
        if(updated == 0) throw new IllegalArgumentException("Solde insuffisant");
        Wallet w = walletRepo.findByUtilisateur(u).orElseThrow();
        // notifier via SSE
        walletSseService.sendBalanceUpdate(u.getEmail(), w.getSolde());
        return w;
    }

    @Transactional
    public void supprimerWallet(Utilisateur u){
        try {
            // optionnel : fermer les SSE côté client
            walletSseService.complete(u.getEmail());
        } catch (Exception ignore) {}

        walletRepo.deleteByUtilisateur(u);
    }

}
