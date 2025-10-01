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

@Service // Indique que c'est une classe de service Spring
public class UtilisateurService {
    @Autowired
    private UtilisateurRepository utilisateurRepo;
    @Autowired
    private WalletRepository walletRepo;

    // BCrypt pour hacher les mots de passe
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional // Garantit que tout s'exécute dans une transaction
    public Utilisateur inscrire(String email, String pseudo, String motDePassePlain) {
        // Hachage du mot de passe
        String hash = passwordEncoder.encode(motDePassePlain);

        // Création de l’objet utilisateur avec Lombok Builder
        Utilisateur u = Utilisateur.builder()
                .email(email)
                .pseudo(pseudo)
                .motDePasseHash(hash)
                .dateCreation(LocalDateTime.now())
                .active(true)
                .role("USER") // rôle par défaut
                .build();

        // Sauvegarde en BDD
        Utilisateur saved = utilisateurRepo.save(u);

        // Création d’un portefeuille initial avec 1000 crédits
        Wallet w = Wallet.builder()
                .utilisateur(saved)
                .solde(1000L)
                .build();
        walletRepo.save(w);

        return saved;
    }

    // Recherche d'un utilisateur par email
    public Utilisateur trouverParEmail(String email){
        return utilisateurRepo.findByEmail(email).orElse(null);
    }

    // Vérification du mot de passe avec BCrypt
    public boolean verifierMotDePasse(Utilisateur utilisateur, String motDePassePlain) {
        return passwordEncoder.matches(motDePassePlain, utilisateur.getMotDePasseHash());
    }
}
