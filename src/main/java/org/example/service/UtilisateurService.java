package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.repo.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UtilisateurService {
    @Autowired
    private UtilisateurRepository utilisateurRepo;
    @Autowired
    private WalletRepository walletRepo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Utilisateur inscrire(String email, String pseudo, String motDePassePlain) {
        String hash = passwordEncoder.encode(motDePassePlain);
        Utilisateur u = new Utilisateur(email, pseudo, hash);
        Utilisateur saved = utilisateurRepo.save(u);
        // créer wallet initial avec 1000 crédits
        Wallet w = new Wallet(saved, 1000L);
        walletRepo.save(w);
        return saved;
    }

    public Utilisateur trouverParEmail(String email){
        return utilisateurRepo.findByEmail(email).orElse(null);
    }

    public boolean verifierMotDePasse(Utilisateur utilisateur, String motDePassePlain) {
        return passwordEncoder.matches(motDePassePlain, utilisateur.getMotDePasseHash());
    }
}
