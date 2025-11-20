package org.example.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.example.dto.LeaderboardEntryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private TypedQuery<Object[]> query;

    @InjectMocks
    private LeaderboardService leaderboardService;

    @Test
    void topByCredits_shouldClampLimitBetween1And200_andReturnMappedDtos() {
        // ARRANGE
        // On simule le createQuery pour n'importe quelle string
        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);

        // On simule 3 lignes renvoyées par la requête JPQL
        Object[] row1 = new Object[] { "Alice", 3000L };
        Object[] row2 = new Object[] { "Bob", null };   // solde null => 0L
        Object[] row3 = new Object[] { "Charlie", 1500L };

        when(query.getResultList()).thenReturn(List.of(row1, row2, row3));

        // ACT
        List<LeaderboardEntryDto> result = leaderboardService.topByCredits(500); // > 200, doit être clampé à 200

        // ASSERT : clamp du limit -> setMaxResults appelé avec 200
        ArgumentCaptor<Integer> maxCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(query).setMaxResults(maxCaptor.capture());
        Integer maxUsed = maxCaptor.getValue();
        assertThat(maxUsed).isEqualTo(200);

        // ASSERT : mapping des lignes en DTO
        assertThat(result).hasSize(3);

        LeaderboardEntryDto e1 = result.get(0);
        LeaderboardEntryDto e2 = result.get(1);
        LeaderboardEntryDto e3 = result.get(2);

        // Rang croissant à partir de 1
        assertThat(e1.getRang()).isEqualTo(1);
        assertThat(e2.getRang()).isEqualTo(2);
        assertThat(e3.getRang()).isEqualTo(3);

        // Pseudos
        assertThat(e1.getPseudo()).isEqualTo("Alice");
        assertThat(e2.getPseudo()).isEqualTo("Bob");
        assertThat(e3.getPseudo()).isEqualTo("Charlie");

        // Soldes (null -> 0L)
        assertThat(e1.getSolde()).isEqualTo(3000L);
        assertThat(e2.getSolde()).isEqualTo(0L);
        assertThat(e3.getSolde()).isEqualTo(1500L);
    }

    @Test
    void topByCredits_shouldClampLimitTo1_whenLimitIsZeroOrNegative() {
        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        leaderboardService.topByCredits(0);   // <=0 -> clamp à 1
        leaderboardService.topByCredits(-10); // <=0 -> clamp à 1

        // On vérifie au moins une fois que setMaxResults(1) a été utilisé
        verify(query, atLeastOnce()).setMaxResults(1);
    }
}
