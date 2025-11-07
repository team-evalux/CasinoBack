package org.example.repo;

import org.example.model.Wallet;
import org.example.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUtilisateur(Utilisateur utilisateur);

    @Transactional
    @Modifying
    @Query("update Wallet w set w.solde = w.solde + :amount where w.utilisateur = :u")
    int incrementSolde(@Param("u") Utilisateur utilisateur, @Param("amount") long amount);

    @Transactional
    @Modifying
    @Query("update Wallet w set w.solde = w.solde - :amount where w.utilisateur = :u and w.solde >= :amount")
    int decrementSoldeIfEnough(@Param("u") Utilisateur utilisateur, @Param("amount") long amount);


    @Modifying
    @Query("delete from Wallet w where w.utilisateur = :u")
    int deleteByUtilisateur(@Param("u") Utilisateur u);
}
