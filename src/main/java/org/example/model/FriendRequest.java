package org.example.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
public class FriendRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fromEmail;
    private String toEmail;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    private Instant createdAt = Instant.now();

    public enum Status {
        PENDING, ACCEPTED, REFUSED
    }
}
