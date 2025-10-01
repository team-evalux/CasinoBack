package org.example.security;

import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service // Ce composant est découvert automatiquement par Spring (bean injectable)
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UtilisateurRepository utilisateurRepo; // accès à la DB pour trouver un utilisateur

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Cherche l’utilisateur par email (= username)
        Utilisateur u = utilisateurRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));

        // Détermine le rôle (par défaut "USER")
        String role = (u.getRole() != null && !u.getRole().isBlank()) ? u.getRole() : "USER";

        // Construit un objet UserDetails que Spring Security utilisera pour authentifier
        return User.withUsername(u.getEmail())
                .password(u.getMotDePasseHash()) // mot de passe hashé stocké en DB
                .roles(role) // ex: "USER" ou "ADMIN" (Spring ajoute "ROLE_" automatiquement)
                .build();
    }
}
