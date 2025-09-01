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

    public Wallet getWalletParUtilisateur(Utilisateur u){
        return walletRepo.findByUtilisateur(u).orElseGet(() -> {
            Wallet w = new Wallet(u, 0L);
            return walletRepo.save(w);
        });
    }

    @Transactional
    public Wallet crediter(Utilisateur u, long montant){
        Wallet w = getWalletParUtilisateur(u);
        w.setSolde(w.getSolde() + montant);
        return walletRepo.save(w);
    }

    @Transactional
    public Wallet debiter(Utilisateur u, long montant){
        Wallet w = getWalletParUtilisateur(u);
        long nouveau = w.getSolde() - montant;
        if(nouveau < 0) throw new IllegalArgumentException("Solde insuffisant");
        w.setSolde(nouveau);
        return walletRepo.save(w);
    }
}
