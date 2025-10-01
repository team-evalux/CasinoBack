package org.example.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration // Indique que c'est une classe de configuration Spring
@EnableMethodSecurity // Permet d'utiliser @PreAuthorize/@Secured dans les services/contrôleurs
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Autowired
    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean // Bean Spring : encodeur de mots de passe (BCrypt est l’algo recommandé)
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean // Définit la config de sécurité principale
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()); // désactive la protection CSRF (inutile en API stateless)
        http.cors(cors -> cors.configurationSource(corsConfigurationSource())); // active CORS
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)); // pas de session HTTP

        http.authorizeHttpRequests(auth -> auth
                // Autorise les requêtes "OPTIONS" (préflight CORS)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Endpoints publics
                .requestMatchers("/api/auth/**", "/h2-console/**", "/ws/**").permitAll()

                // Endpoints protégés
                .requestMatchers("/api/bonus/**").authenticated()
                .requestMatchers("/api/bj/**").authenticated()

                // Tout le reste = nécessite d’être connecté
                .anyRequest().authenticated()
        );

        // Autorise l'affichage H2-console dans un frame (utile en dev)
        http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        // Ajoute notre filtre JWT AVANT l’authentification par défaut de Spring
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean // Configuration CORS (Angular sur http://localhost:4200 autorisé)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(List.of("http://localhost:4200")); // origine autorisée
        config.setAllowedHeaders(List.of("*")); // tous headers acceptés
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean // Fournit l’AuthenticationManager (utilisé par Spring pour authentifier)
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
