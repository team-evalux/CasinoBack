package org.example.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.example.dto.LeaderboardEntryDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LeaderboardService {

    @PersistenceContext
    private EntityManager em;

    /**
     * Retourne le top N utilisateurs actifs triés par solde décroissant.
     * Rang attribué côté Java (1..N).
     */
    @Transactional
    public List<LeaderboardEntryDto> topByCredits(int limit) {
        int max = Math.max(1, Math.min(limit, 200)); // borne 1..200

        // JPQL simple : Wallet w -> Utilisateur u, uniquement actifs, tri par solde décroissant
        var q = em.createQuery("""
                select u.pseudo, w.solde
                from Wallet w
                join w.utilisateur u
                where u.active = true
                order by w.solde desc, u.pseudo asc
                """, Object[].class);
        q.setMaxResults(max);

        List<Object[]> rows = q.getResultList();
        List<LeaderboardEntryDto> result = new ArrayList<>(rows.size());
        int rang = 1;
        for (Object[] r : rows) {
            String pseudo = (String) r[0];
            Long solde = (Long) r[1];
            result.add(LeaderboardEntryDto.builder()
                    .rang(rang++)
                    .pseudo(pseudo)
                    .solde(solde != null ? solde : 0L)
                    .build());
        }
        return result;
    }
}
