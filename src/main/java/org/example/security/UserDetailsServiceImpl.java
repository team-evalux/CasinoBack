package org.example.security;

import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UtilisateurRepository utilisateurRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Utilisateur u = utilisateurRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé"));

        String role = (u.getRole() != null && !u.getRole().isBlank()) ? u.getRole() : "USER";
        return User.withUsername(u.getEmail())
                .password(u.getMotDePasseHash())
                .roles(role) // Spring ajoutera "ROLE_" en interview
                .build();
    }
}
