// src/main/java/org/example/repo/ChatAggregateRepository.java
package org.example.repo;

import org.example.model.ChatAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatAggregateRepository extends JpaRepository<ChatAggregate, Long> {
}
