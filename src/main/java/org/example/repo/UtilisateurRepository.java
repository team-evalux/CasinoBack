package org.example.repo;

import org.example.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Repository JPA pour la gestion des utilisateurs
// Hérite de JpaRepository → fournit toutes les méthodes CRUD de base
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    // Recherche d’un utilisateur par email (Optional car peut être vide)
    Optional<Utilisateur> findByEmail(String email);
}
