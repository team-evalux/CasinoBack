package org.example.repo;

import org.example.model.blackjack.BjTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BjTableRepository extends JpaRepository<BjTableEntity, Long> {
    List<BjTableEntity> findByIsPrivateFalse();
}

