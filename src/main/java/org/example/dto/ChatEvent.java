// src/main/java/org/example/dto/ChatEvent.java
package org.example.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatEvent {

    public enum Type {
        MESSAGE, // nouveau message
        DELETE,  // suppression d'un message
        CLEAR    // vidage complet
    }

    private Type type;
    private ChatMessage message; // rempli pour MESSAGE
    private Long id;             // rempli pour DELETE
}
