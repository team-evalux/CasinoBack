package org.example.repo;

import org.example.model.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

    // Trouver toutes les demandes envoyées à un utilisateur avec un statut donné
    List<FriendRequest> findByToEmailAndStatus(String toEmail, FriendRequest.Status status);

    // Vérifier si une demande existe déjà (pour éviter doublons)
    Optional<FriendRequest> findByFromEmailAndToEmailAndStatus(
            String fromEmail,
            String toEmail,
            FriendRequest.Status status
    );
}
