// src/main/java/org/example/model/ChatMessage.java
package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatMessage {
    private Long id;
    private String pseudo;
    private String contenu;
    private LocalDateTime date;
}
