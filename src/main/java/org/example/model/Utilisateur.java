package org.example.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateur")
public class Utilisateur {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String pseudo;

    @Column(nullable = false)
    private String motDePasseHash;

    private LocalDateTime dateCreation = LocalDateTime.now();

    private boolean active = true;

    // constructeurs, getters et setters
    public Utilisateur() {}
    public Utilisateur(String email, String pseudo, String motDePasseHash) {
        this.email = email;
        this.pseudo = pseudo;
        this.motDePasseHash = motDePasseHash;
    }
    // getters / setters...
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}
    public String getEmail(){return email;}
    public void setEmail(String email){this.email=email;}
    public String getPseudo(){return pseudo;}
    public void setPseudo(String pseudo){this.pseudo=pseudo;}
    public String getMotDePasseHash(){return motDePasseHash;}
    public void setMotDePasseHash(String motDePasseHash){this.motDePasseHash=motDePasseHash;}
    public LocalDateTime getDateCreation(){return dateCreation;}
    public boolean isActive(){return active;}
    public void setActive(boolean active){this.active=active;}
}
