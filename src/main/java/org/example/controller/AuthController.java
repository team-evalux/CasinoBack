package org.example.controller;

import org.example.dto.*;
import org.example.model.Utilisateur;
import org.example.model.VerificationCode;
import org.example.security.JwtUtil;
import org.example.service.UtilisateurService;
import org.example.service.VerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UtilisateurService utilisateurService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private JwtUtil jwtUtil;

    // --- Étape 1 : envoyer un code d'inscription ---
    @PostMapping("/register/send-code")
    public ResponseEntity<?> envoyerCodeInscription(@RequestBody EmailDTO req) {
        if (utilisateurService.trouverParEmail(req.email) != null) {
            return ResponseEntity.badRequest().body(Map.of("error","Email déjà utilisé"));
        }
        if (utilisateurService.trouverParPseudo(req.pseudo) != null) {
            return ResponseEntity.badRequest().body(Map.of("error","Pseudo déjà utilisé"));
        }
        verificationService.envoyerCode(req.email, VerificationCode.Type.REGISTER);
        return ResponseEntity.ok(Map.of("message", "Code envoyé à " + req.email));
    }


    // --- Étape 2 : valider code et créer compte ---
    @PostMapping("/register/verify")
    public ResponseEntity<?> verifierCodeEtInscrire(@RequestBody RegisterRequest req) {
        boolean ok = verificationService.verifierCode(req.email, req.code, VerificationCode.Type.REGISTER);
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Code invalide ou expiré"
            ));        }
        Utilisateur u = utilisateurService.inscrire(req.email, req.pseudo, req.motDePasse);
        String token = jwtUtil.genererToken(u.getEmail());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "email", u.getEmail(),
                "pseudo", u.getPseudo(),
                "role", u.getRole(),
                "message", "Inscription réussie et vérifiée"
        ));    }

    // --- Mot de passe oublié ---
    @PostMapping("/forgot/send-code")
    public ResponseEntity<?> envoyerCodeReset(@RequestBody EmailDTO req) {
        Utilisateur u = utilisateurService.trouverParEmail(req.email);
        if (u == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Aucun compte trouvé pour cet email"));

        verificationService.envoyerCode(req.email, VerificationCode.Type.RESET_PASSWORD);
        return ResponseEntity.ok(Map.of("message", "Code envoyé"));
    }


    @PostMapping("/forgot/reset")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDTO req) {
        boolean ok = verificationService.verifierCode(req.email, req.code, VerificationCode.Type.RESET_PASSWORD);
        if (!ok) {
            return ResponseEntity.badRequest().body(Map.of("error", "Code invalide ou expiré"));
        }

        utilisateurService.mettreAJourMotDePasse(req.email, req.nouveauMotDePasse);
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès"));
    }


    // --- Login classique ---
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
