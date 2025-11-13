package org.example.repo;

import org.example.model.TipDailyAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface TipDailyAggregateRepository extends JpaRepository<TipDailyAggregate, Long> {

    Optional<TipDailyAggregate> findByUtilisateurIdAndDate(Long utilisateurId, LocalDate date);

}
