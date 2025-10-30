// src/main/java/org/example/controller/SupportController.java
package org.example.controller;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final JavaMailSender mailSender;
    private final UtilisateurRepository utilisateurRepo;
    private final String toEmail;

    // cache simple anti-spam : userId → timestamp dernier envoi
    private final Map<Long, Instant> lastSent = new ConcurrentHashMap<>();

    public SupportController(JavaMailSender mailSender,
                             UtilisateurRepository utilisateurRepo,
                             @Value("${app.support.to:evalux.casino@gmail.com}") String toEmail) {
        this.mailSender = mailSender;
        this.utilisateurRepo = utilisateurRepo;
        this.toEmail = toEmail;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> sendSupportMail(@RequestBody Map<String, String> body, Authentication auth) {
        if (auth == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Non authentifié"));
        }

        Utilisateur u = utilisateurRepo.findByEmail(auth.getName()).orElseThrow();
        Long uid = u.getId();

        Instant now = Instant.now();
        Instant last = lastSent.get(uid);
        if (last != null && now.isBefore(last.plusSeconds(300))) { // 5 minutes
            long remaining = 300 - (now.getEpochSecond() - last.getEpochSecond());
            return ResponseEntity.badRequest().body(Map.of("error", "Veuillez patienter " + remaining + "s avant de renvoyer un message."));
        }

        String nom = body.getOrDefault("nom", "Utilisateur inconnu");
        String email = body.getOrDefault("email", u.getEmail());
        String sujet = body.getOrDefault("sujet", "(sans sujet)");
        String message = body.getOrDefault("message", "(vide)");

        if (nom.length() > 50 || email.length() > 100 || sujet.length() > 100 || message.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le message est trop long."));
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("🛠️ Nouveau message de support — " + sujet);
            helper.setText(
                "Nom : " + nom + "\n" +
                "Email : " + email + "\n" +
                "Utilisateur ID : " + uid + "\n\n" +
                "Message :\n" + message
            );

            mailSender.send(msg);
            lastSent.put(uid, now);

            return ResponseEntity.ok(Map.of("success", true, "message", "Message envoyé avec succès."));
        } catch (MessagingException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Échec de l’envoi de l’email."));
        }
    }
}
