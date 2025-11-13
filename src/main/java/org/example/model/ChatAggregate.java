// src/main/java/org/example/model/ChatAggregate.java
package org.example.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ChatAggregate {

    @Id
    private Long id = 1L; // unique aggregate global pour tout le chat

    @Lob
    @Column(columnDefinition = "TEXT")
    private String entriesJson; // liste JSON de messages

    private LocalDateTime updatedAt;
}
