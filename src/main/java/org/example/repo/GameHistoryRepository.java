// src/main/java/org/example/repo/GameHistoryRepository.java
package org.example.repo;

import org.example.model.GameHistory;
import org.example.model.Utilisateur;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameHistoryRepository extends JpaRepository<GameHistory, Long> {
    List<GameHistory> findByUtilisateurOrderByCreatedAtDesc(Utilisateur utilisateur, Pageable pageable);
    List<GameHistory> findByUtilisateurAndGameOrderByCreatedAtDesc(Utilisateur utilisateur, String game, Pageable pageable);
}
