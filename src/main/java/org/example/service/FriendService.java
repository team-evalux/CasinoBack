package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.Friend;
import org.example.model.FriendRequest;
import org.example.model.Utilisateur;
import org.example.repo.FriendRepository;
import org.example.repo.FriendRequestRepository;
import org.example.repo.UtilisateurRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepo;
    private final FriendRequestRepository requestRepo;
    private final UtilisateurRepository userRepo;
    private final WalletService walletService;

    // ✅ envoyer une invitation
    public FriendRequest sendRequest(String fromEmail, String toEmail) {
        if (fromEmail.equals(toEmail)) {
            throw new IllegalArgumentException("Impossible de t’ajouter toi-même");
        }

        // Vérifie si le destinataire existe
        userRepo.findByEmail(toEmail).orElseThrow(() ->
                new IllegalArgumentException("Utilisateur inexistant"));

        // Vérifie si une demande existe déjà
        return requestRepo.findByFromEmailAndToEmailAndStatus(fromEmail, toEmail, FriendRequest.Status.PENDING)
                .orElseGet(() -> {
                    FriendRequest req = new FriendRequest();
                    req.setFromEmail(fromEmail);
                    req.setToEmail(toEmail);
                    req.setStatus(FriendRequest.Status.PENDING);
                    req.setCreatedAt(Instant.now());
                    return requestRepo.save(req);
                });
    }

    // ✅ voir les invitations reçues
    public List<FriendRequest> getRequests(String toEmail) {
        return requestRepo.findByToEmailAndStatus(toEmail, FriendRequest.Status.PENDING);
    }

    // ✅ accepter une invitation
    public void accept(Long requestId, String toEmail) {
        FriendRequest req = requestRepo.findById(requestId).orElseThrow();
        if (!req.getToEmail().equals(toEmail)) {
            throw new IllegalStateException("Pas ton invitation");
        }

        req.setStatus(FriendRequest.Status.ACCEPTED);
        requestRepo.save(req);

        // Crée la relation d’amis dans les deux sens
        createFriendRelation(req.getFromEmail(), req.getToEmail());
        createFriendRelation(req.getToEmail(), req.getFromEmail());
    }

    private void createFriendRelation(String owner, String friend) {
        if (friendRepo.findByOwnerEmailAndFriendEmail(owner, friend).isEmpty()) {
            Friend f = new Friend();
            f.setOwnerEmail(owner);
            f.setFriendEmail(friend);
            f.setOnline(false);
            f.setLastSeen(Instant.now());
            friendRepo.save(f);
        }
    }

    // ✅ refuser
    public void refuse(Long requestId, String toEmail) {
        FriendRequest req = requestRepo.findById(requestId).orElseThrow();
        if (!req.getToEmail().equals(toEmail)) {
            throw new IllegalStateException("Pas ton invitation");
        }
        req.setStatus(FriendRequest.Status.REFUSED);
        requestRepo.save(req);
    }

    // ✅ liste des amis
    public List<Friend> listFriends(String owner) {
        return friendRepo.findByOwnerEmail(owner);
    }

    // ✅ mise à jour statut online/offline
    public void setOnline(String email, boolean online) {
        Instant now = Instant.now();

        // Les relations où je suis owner
        List<Friend> asOwner = friendRepo.findByOwnerEmail(email);
        for (Friend f : asOwner) {
            f.setOnline(online);
            f.setLastSeen(now);
            friendRepo.save(f);
        }

        // Les relations où je suis ami
        List<Friend> asFriend = friendRepo.findAll().stream()
                .filter(f -> f.getFriendEmail().equals(email))
                .toList();
        for (Friend f : asFriend) {
            f.setOnline(online);
            f.setLastSeen(now);
            friendRepo.save(f);
        }
    }

    // ✅ envoi de crédits (limite 1000/jour par ami)
    public void sendCredits(String fromEmail, String toEmail, long amount) {
        if (amount <= 0 || amount > 1000) {
            throw new IllegalArgumentException("Montant invalide (max 1000/jour)");
        }
        Utilisateur from = userRepo.findByEmail(fromEmail).orElseThrow();
        Utilisateur to = userRepo.findByEmail(toEmail).orElseThrow();
        walletService.debiter(from, amount);
        walletService.crediter(to, amount);
    }
}
