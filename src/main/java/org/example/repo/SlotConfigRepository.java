// src/main/java/org/example/repo/SlotConfigRepository.java
package org.example.repo;

import org.example.model.SlotConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotConfigRepository extends JpaRepository<SlotConfig, Long> {
}
