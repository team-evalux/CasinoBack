package org.example.service;

import org.example.repo.TipDailyAggregateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TipDailyResetService {

    @Autowired
    private TipDailyAggregateRepository tipRepo;

    /**
     * Reset chaque jour à 2h du matin
     * Expression cron format Spring : second minute hour day month dayOfWeek
     * Ici : 0 0 2 * * *  ->  2:00:00 du matin
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Paris")
    public void resetDailyTips() {
        System.out.println("⚡ Reset automatique des tips journaliers (2h du matin)");

        tipRepo.deleteAll();
    }
}
