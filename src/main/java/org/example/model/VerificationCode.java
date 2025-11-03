package org.example.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 4)
    private String code;

    @Column(nullable = false)
    private LocalDateTime expiration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type; // REGISTER ou RESET_PASSWORD

    public enum Type {
        REGISTER,
        RESET_PASSWORD
    }
}
