package org.example.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component // Classe utilitaire Spring pour gérer les JWT
public class JwtUtil {
    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256); // clé secrète pour signer les tokens
    private long jwtExpirationMs = 86400000; // durée de validité du token (24h)

    // Génère un token JWT pour un utilisateur donné
    public String genererToken(String subject){
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject) // email
                .setIssuedAt(now) // date de création
                .setExpiration(new Date(now.getTime() + jwtExpirationMs)) // date d’expiration
                .signWith(key, SignatureAlgorithm.HS256) // signature
                .compact();
    }

    // Récupère le "subject" (ici l’email) depuis un token
    public String extraireSubject(String token){
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    // Vérifie si un token est valide
    public boolean validerToken(String token){
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false; // invalide (expiré, signature incorrecte, etc.)
        }
    }
}
