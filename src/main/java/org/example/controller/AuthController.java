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

@RestController // Indique que c'est un contrôleur REST
@RequestMapping("/api/auth") // Les endpoints commencent par /api/auth
public class AuthController {
    @Autowired
    private UtilisateurService utilisateurService;
    @Autowired
    private JwtUtil jwtUtil;

    // Endpoint d'inscription
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req){
        // Vérifie si l'email est déjà pris
        if(utilisateurService.trouverParEmail(req.email)!=null){
            return ResponseEntity.badRequest().body("Email déjà utilisé");
        }
        // Création de l’utilisateur
        Utilisateur u = utilisateurService.inscrire(req.email, req.pseudo, req.motDePasse);
        // Génération du token JWT lié à son email
        String token = jwtUtil.genererToken(u.getEmail());
        // Renvoie le token et les infos utilisateur
        return ResponseEntity.ok(new AuthResponse(token, u.getEmail(), u.getPseudo(), u.getRole()));
    }

    // Endpoint de connexion
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest req){
        // Vérifie si l'utilisateur existe
        Utilisateur u = utilisateurService.trouverParEmail(req.email);
        // Vérifie si le mot de passe est correct
        if(u==null || !utilisateurService.verifierMotDePasse(u, req.motDePasse)){
            return ResponseEntity.status(401).body("Identifiants invalides");
        }
        // Génération du token JWT si les identifiants sont valides
        String token = jwtUtil.genererToken(u.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, u.getEmail(), u.getPseudo(), u.getRole()));
    }
}
