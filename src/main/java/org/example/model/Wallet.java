package org.example.model;

import jakarta.persistence.*;

@Entity
@Table(name = "wallet")
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    private Utilisateur utilisateur;

    @Column(nullable = false)
    private Long solde = 0L; // crédits en entier

    public Wallet() {}
    public Wallet(Utilisateur utilisateur, Long solde) {
        this.utilisateur = utilisateur;
        this.solde = solde;
    }
    // getters / setters
    public Long getId(){return id;}
    public Utilisateur getUtilisateur(){return utilisateur;}
    public void setUtilisateur(Utilisateur u){this.utilisateur=u;}
    public Long getSolde(){return solde;}
    public void setSolde(Long s){this.solde=s;}
}
