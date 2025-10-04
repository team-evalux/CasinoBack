package org.example.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

/**
 * Utilitaire JWT.
 * Le secret doit être fourni via la propriété "jwt.secret" (BASE64 encoded key).
 * Exemple (application.properties):
 *   jwt.secret=BASE64_ENCODED_32_BYTES_KEY
 *   jwt.expiration-ms=86400000
 */
@Component
public class JwtUtil {

    private Key key;

    @Value("${jwt.secret:}")
    private String jwtSecretBase64;

    @Value("${jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    @PostConstruct
    public void init() {
        if (jwtSecretBase64 == null || jwtSecretBase64.isBlank()) {
            throw new IllegalStateException("jwt.secret must be set (base64).");
        }
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecretBase64);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String genererToken(String subject){
        Date now = new Date();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extraireSubject(String token){
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validerToken(String token){
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
