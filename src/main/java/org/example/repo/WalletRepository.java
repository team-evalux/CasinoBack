package org.example.repo;

import org.example.model.Wallet;
import org.example.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUtilisateur(Utilisateur utilisateur);
}
