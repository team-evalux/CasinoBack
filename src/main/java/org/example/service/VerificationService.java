package org.example.service;

import org.example.model.VerificationCode;
import org.example.repo.VerificationCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class VerificationService {

    @Autowired
    private VerificationCodeRepository repo;

    @Autowired
    private MailService mailService;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void envoyerCode(String email, VerificationCode.Type type) {
        String code = String.format("%04d", random.nextInt(10000));
        LocalDateTime expiration = LocalDateTime.now().plusMinutes(10);

        repo.deleteByEmail(email); // un seul code actif par email
        repo.save(VerificationCode.builder()
                .email(email)
                .code(code)
                .expiration(expiration)
                .type(type)
                .build());

        String sujet = (type == VerificationCode.Type.REGISTER)
                ? "Vérification de votre adresse e-mail"
                : "Code de réinitialisation de mot de passe";

        String contenu = "Votre code de vérification est : " + code + "\n\n"
                + "Ce code expire dans 10 minutes.";

        mailService.envoyer(email, sujet, contenu);
    }

    public boolean verifierCode(String email, String code, VerificationCode.Type type) {
        return repo.findTopByEmailAndTypeOrderByExpirationDesc(email, type)
                .filter(vc -> vc.getExpiration().isAfter(LocalDateTime.now()))
                .filter(vc -> vc.getCode().equals(code))
                .isPresent();
    }
}
