package org.example.service;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.repo.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

        // création de l'utilisateur via le builder (compatible avec Lombok @Builder)
        Utilisateur u = Utilisateur.builder()
                .email(email)
                .pseudo(pseudo)
                .motDePasseHash(hash)
                .dateCreation(LocalDateTime.now())
                .active(true)
                .role("USER") // par défaut
                .build();

        Utilisateur saved = utilisateurRepo.save(u);

        // créer wallet initial avec 1000 crédits (via builder si Wallet utilise Lombok)
        Wallet w = Wallet.builder()
                .utilisateur(saved)
                .solde(1000L)
                .build();
        walletRepo.save(w);

        return saved;
    }

    public Utilisateur trouverParEmail(String email){
        return utilisateurRepo.findByEmail(email).orElse(null);
    }

    public boolean verifierMotDePasse(Utilisateur utilisateur, String motDePassePlain) {
        return passwordEncoder.matches(motDePassePlain, utilisateur.getMotDePasseHash());
    }

    @Transactional
    public void mettreAJourMotDePasse(String email, String nouveauMotDePasse) {
        Utilisateur u = utilisateurRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        u.setMotDePasseHash(passwordEncoder.encode(nouveauMotDePasse));
        utilisateurRepo.save(u);
    }

}
