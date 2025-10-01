// src/main/java/org/example/repo/CoinFlipConfigRepository.java
package org.example.repo;

import org.example.model.CoinFlipConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoinFlipConfigRepository extends JpaRepository<CoinFlipConfig, Long> {
}
