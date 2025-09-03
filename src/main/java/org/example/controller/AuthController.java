package org.example.controller;

import org.example.dto.AuthRequest;
import org.example.dto.AuthResponse;
import org.example.dto.RegisterRequest;
import org.example.model.Utilisateur;
import org.example.service.UtilisateurService;
import org.example.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private UtilisateurService utilisateurService;
    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req){
        if(utilisateurService.trouverParEmail(req.email)!=null){
            return ResponseEntity.badRequest().body("Email déjà utilisé");
        }
        Utilisateur u = utilisateurService.inscrire(req.email, req.pseudo, req.motDePasse);
        String token = jwtUtil.genererToken(u.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, u.getEmail(), u.getPseudo(), u.getRole()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req){
        Utilisateur u = utilisateurService.trouverParEmail(req.email);
        if(u==null || !utilisateurService.verifierMotDePasse(u, req.motDePasse)){
            return ResponseEntity.status(401).body("Identifiants invalides");
        }
        String token = jwtUtil.genererToken(u.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, u.getEmail(), u.getPseudo(), u.getRole()));
    }
}