package org.example.repo;

import org.example.model.Wallet;
import org.example.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Repository JPA pour gérer les portefeuilles (Wallet) liés aux utilisateurs
// Hérite de JpaRepository → fournit toutes les opérations CRUD de base
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // Recherche d’un portefeuille à partir d’un utilisateur
    Optional<Wallet> findByUtilisateur(Utilisateur utilisateur);

    // Incrémente le solde du wallet de l’utilisateur
    @Transactional // garantit que l’opération est dans une transaction
    @Modifying // indique que c’est une requête d’écriture (UPDATE, DELETE, etc.)
    @Query("update Wallet w set w.solde = w.solde + :amount where w.utilisateur = :u")
    int incrementSolde(@Param("u") Utilisateur utilisateur, @Param("amount") long amount);
    // Retourne le nombre de lignes mises à jour (0 ou 1 normalement)

    // Décrémente le solde seulement si le solde actuel est suffisant (>= amount)
    @Transactional
    @Modifying
    @Query("update Wallet w set w.solde = w.solde - :amount where w.utilisateur = :u and w.solde >= :amount")
    int decrementSoldeIfEnough(@Param("u") Utilisateur utilisateur, @Param("amount") long amount);
    // Retourne 1 si la décrémentation a réussi, 0 si solde insuffisant
}
