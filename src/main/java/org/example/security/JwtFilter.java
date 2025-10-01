package org.example.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component // Filtre JWT appliqué à toutes les requêtes HTTP
public class JwtFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Récupère le header Authorization
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7); // enlève "Bearer "
        } else if (request.getParameter("token") != null) {
            token = request.getParameter("token"); // support ?token=...
        }

        // Si token présent et valide
        if (token != null && jwtUtil.validerToken(token)) {
            String email = jwtUtil.extraireSubject(token); // récupère email depuis le token
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // Crée un objet d’authentification
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // Place l’utilisateur dans le contexte de sécurité Spring
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        // Passe au filtre suivant (chaîne obligatoire)
        filterChain.doFilter(request, response);
    }
}
