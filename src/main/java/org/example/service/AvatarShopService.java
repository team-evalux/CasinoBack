package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.*;
import org.example.repo.AvatarRepository;
import org.example.repo.UtilisateurAvatarRepository;
import org.example.repo.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvatarShopService {

    private final AvatarRepository avatarRepo;
    private final UtilisateurAvatarRepository utilisateurAvatarRepo;
    private final UtilisateurRepository utilisateurRepo;
    private final WalletService walletService;

    // Liste des avatars en boutique
    public List<Avatar> listeBoutique() {
        return avatarRepo.findByActifTrueOrderByPrixAsc();
    }

    // Inventaire de l'utilisateur connecté
    public List<UtilisateurAvatar> inventaire(String email) {
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        return utilisateurAvatarRepo.findByUtilisateur(u);
    }

    // Achat d'un avatar
    @Transactional
    public UtilisateurAvatar acheter(String email, Long avatarId) {
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();

        Avatar avatar = avatarRepo.findById(avatarId)
                .filter(Avatar::isActif)
                .orElseThrow(() -> new IllegalArgumentException("Avatar introuvable"));

        if (utilisateurAvatarRepo.existsByUtilisateurAndAvatar(u, avatar)) {
            throw new IllegalStateException("Avatar déjà possédé");
        }

        // Débitera ou lèvera IllegalArgumentException("Solde insuffisant")
        walletService.debiter(u, avatar.getPrix());

        UtilisateurAvatar ua = UtilisateurAvatar.builder()
                .utilisateur(u)
                .avatar(avatar)
                .equipe(false)
                .dateAcquisition(LocalDateTime.now())
                .build();

        // Si aucun avatar équipé, on équipe celui-ci automatiquement
        if (utilisateurAvatarRepo.findByUtilisateurAndEquipeTrue(u).isEmpty()) {
            ua.setEquipe(true);
        }

        return utilisateurAvatarRepo.save(ua);
    }

    // Équiper un avatar possédé
    @Transactional
    public UtilisateurAvatar equiper(String email, Long avatarId) {
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        Avatar avatar = avatarRepo.findById(avatarId)
                .orElseThrow(() -> new IllegalArgumentException("Avatar introuvable"));

        UtilisateurAvatar ua = utilisateurAvatarRepo.findByUtilisateurAndAvatar(u, avatar)
                .orElseThrow(() -> new IllegalArgumentException("Avatar non possédé"));

        utilisateurAvatarRepo.desequiperTout(u);
        ua.setEquipe(true);
        return utilisateurAvatarRepo.save(ua);
    }

    // Récupérer l'avatar actuellement équipé
    public UtilisateurAvatar avatarEquipe(String email) {
        Utilisateur u = utilisateurRepo.findByEmail(email).orElseThrow();
        return utilisateurAvatarRepo.findByUtilisateurAndEquipeTrue(u).orElse(null);
    }

    // Optionnel : avatar par défaut à la création du compte
    @Transactional
    public void assignerAvatarDefaut(Utilisateur utilisateur) {
        avatarRepo.findFirstByActifTrueAndDefautTrue()
                .ifPresent(avatarDefaut -> {
                    if (!utilisateurAvatarRepo.existsByUtilisateurAndAvatar(utilisateur, avatarDefaut)) {
                        UtilisateurAvatar ua = UtilisateurAvatar.builder()
                                .utilisateur(utilisateur)
                                .avatar(avatarDefaut)
                                .equipe(true)
                                .dateAcquisition(LocalDateTime.now())
                                .build();
                        utilisateurAvatarRepo.save(ua);
                    }
                });
    }
}
