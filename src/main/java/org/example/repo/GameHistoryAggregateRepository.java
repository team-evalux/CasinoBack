// src/main/java/org/example/repo/GameHistoryAggregateRepository.java
package org.example.repo;

import org.example.model.GameHistoryAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameHistoryAggregateRepository extends JpaRepository<GameHistoryAggregate, Long> {
    Optional<GameHistoryAggregate> findByUtilisateurIdAndGame(Long utilisateurId, String game);
    List<GameHistoryAggregate> findByUtilisateurId(Long utilisateurId);
}
