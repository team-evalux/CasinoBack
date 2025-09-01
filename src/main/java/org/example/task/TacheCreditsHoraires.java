package org.example.task;

import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.example.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TacheCreditsHoraires {
    @Autowired
    private UtilisateurRepository utilisateurRepo;
    @Autowired
    private WalletService walletService;

    // Cron : chaque heure pile (minute 0)
    @Scheduled(cron = "0 0 * * * *")
    public void crediterTous(){
        List<Utilisateur> tous = utilisateurRepo.findAll();
        for(Utilisateur u : tous){
            if(u.isActive()){
                walletService.crediter(u, 100L); // +100 crédits chaque heure
            }
        }
    }
}

