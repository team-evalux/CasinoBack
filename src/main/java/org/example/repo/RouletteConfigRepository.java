// src/main/java/org/example/repo/RouletteConfigRepository.java
package org.example.repo;

import org.example.model.RouletteConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouletteConfigRepository extends JpaRepository<RouletteConfig, Long> {
}
