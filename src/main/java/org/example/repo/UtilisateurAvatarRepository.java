package org.example.repo;

import org.example.model.Utilisateur;
import org.example.model.Avatar;
import org.example.model.UtilisateurAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UtilisateurAvatarRepository extends JpaRepository<UtilisateurAvatar, Long> {

    List<UtilisateurAvatar> findByUtilisateur(Utilisateur utilisateur);

    boolean existsByUtilisateurAndAvatar(Utilisateur utilisateur, Avatar avatar);

    Optional<UtilisateurAvatar> findByUtilisateurAndAvatar(Utilisateur utilisateur, Avatar avatar);

    Optional<UtilisateurAvatar> findByUtilisateurAndEquipeTrue(Utilisateur utilisateur);

    @Modifying
    @Query("update UtilisateurAvatar ua set ua.equipe = false where ua.utilisateur = :utilisateur and ua.equipe = true")
    void desequiperTout(@Param("utilisateur") Utilisateur utilisateur);
}
