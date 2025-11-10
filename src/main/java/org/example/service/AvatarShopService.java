package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.dto.AvatarAdminRequest;
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

    // ====== ADMIN ======

    public List<Avatar> listAllForAdmin() {
        return avatarRepo.findAllByOrderByIdAsc();
    }

    @Transactional
    public Avatar createAvatar(AvatarAdminRequest req) {
        validateAdminRequest(req, true);

        if (avatarRepo.findByCode(req.getCode()).isPresent()) {
            throw new IllegalStateException("Code déjà utilisé");
        }

        AvatarRarity rarity = AvatarRarity.valueOf(req.getRarete().toUpperCase());

        // Si on met defaut = true, on enlève le flag defaut des autres
        if (req.isDefaut()) {
            avatarRepo.findAll().forEach(a -> {
                if (a.isDefaut()) {
                    a.setDefaut(false);
                }
            });
        }

        Avatar avatar = Avatar.builder()
                .code(req.getCode().trim())
                .nom(req.getNom().trim())
                .rarete(rarity)
                .prix(req.getPrix())
                .imageUrl(req.getImageUrl())
                .actif(req.isActif())
                .defaut(req.isDefaut())
                .build();

        return avatarRepo.save(avatar);
    }

    @Transactional
    public Avatar updateAvatar(Long id, AvatarAdminRequest req) {
        validateAdminRequest(req, false);

        Avatar avatar = avatarRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avatar introuvable"));

        // code unique si changé
        if (req.getCode() != null && !req.getCode().isBlank()
                && !req.getCode().equals(avatar.getCode())) {
            if (avatarRepo.findByCode(req.getCode()).isPresent()) {
                throw new IllegalStateException("Code déjà utilisé");
            }
            avatar.setCode(req.getCode().trim());
        }

        if (req.getNom() != null && !req.getNom().isBlank()) {
            avatar.setNom(req.getNom().trim());
        }

        if (req.getRarete() != null) {
            AvatarRarity rarity = AvatarRarity.valueOf(req.getRarete().toUpperCase());
            avatar.setRarete(rarity);
        }

        if (req.getPrix() != null && req.getPrix() >= 0) {
            avatar.setPrix(req.getPrix());
        }

        avatar.setActif(req.isActif());
        avatar.setImageUrl(req.getImageUrl());

        if (req.isDefaut()) {
            // ce nouveau devient l'avatar par défaut
            avatarRepo.findAll().forEach(a -> {
                if (!a.getId().equals(avatar.getId()) && a.isDefaut()) {
                    a.setDefaut(false);
                }
            });
            avatar.setDefaut(true);
        } else if (avatar.isDefaut() && !req.isDefaut()) {
            // on autorise à retirer le flag defaut
            avatar.setDefaut(false);
        }

        return avatarRepo.save(avatar);
    }

    @Transactional
    public void disableAvatar(Long id) {
        Avatar avatar = avatarRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avatar introuvable"));
        avatar.setActif(false);
        avatarRepo.save(avatar);
    }

    private void validateAdminRequest(AvatarAdminRequest req, boolean creation) {
        if (creation) {
            if (req.getCode() == null || req.getCode().isBlank())
                throw new IllegalArgumentException("Code obligatoire");
            if (req.getNom() == null || req.getNom().isBlank())
                throw new IllegalArgumentException("Nom obligatoire");
        }
        if (req.getRarete() == null)
            throw new IllegalArgumentException("Rareté obligatoire");
        try {
            AvatarRarity.valueOf(req.getRarete().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Rareté invalide");
        }
        if (req.getPrix() == null || req.getPrix() < 0)
            throw new IllegalArgumentException("Prix invalide");
    }

    @Transactional
    public Avatar setAvatarActive(Long id, boolean active) {
        Avatar avatar = avatarRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Avatar introuvable"));
        avatar.setActif(active);
        return avatarRepo.save(avatar);
    }


}
