package org.example.controller;

import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.example.service.GameHistoryService;
import org.example.service.GameHistoryService.HistoryRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryControllerTest {

    @Mock
    private GameHistoryService historyService;

    @Mock
    private UtilisateurRepository utilisateurRepo;

    @InjectMocks
    private HistoryController historyController;

    private Utilisateur buildUser() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setPseudo("User");
        return u;
    }

    private Authentication authFor(String email) {
        return new UsernamePasswordAuthenticationToken(email, null);
    }

    private HistoryRecord record(long id, String game) {
        return new HistoryRecord(id, game, "outcome", 10L, 20L, 2, "2025-01-01T00:00:00Z");
    }

    @Test
    void myHistory_withoutGame_shouldCallRecentForUser() {
        Utilisateur u = buildUser();
        List<HistoryRecord> list = List.of(record(1L, "coinflip"));

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(historyService.recentForUser(u, 15))
                .thenReturn(list);

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = historyController.myHistory(null, 15, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(list);

        verify(historyService).recentForUser(u, 15);
        verify(historyService, never()).recentForUserByGame(any(), anyString(), anyInt());
    }

    @Test
    void myHistory_withBlankGame_shouldAlsoCallRecentForUser() {
        Utilisateur u = buildUser();
        List<HistoryRecord> list = List.of(record(1L, "roulette"));

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(historyService.recentForUser(u, 10))
                .thenReturn(list);

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = historyController.myHistory("   ", 10, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(list);

        verify(historyService).recentForUser(u, 10);
        verify(historyService, never()).recentForUserByGame(any(), anyString(), anyInt());
    }

    @Test
    void myHistory_withGame_shouldCallRecentForUserByGame() {
        Utilisateur u = buildUser();
        List<HistoryRecord> list = List.of(record(1L, "coinflip"));

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(historyService.recentForUserByGame(u, "coinflip", 20))
                .thenReturn(list);

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = historyController.myHistory("  coinflip  ", 20, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isSameAs(list);

        verify(historyService).recentForUserByGame(u, "coinflip", 20);
        verify(historyService, never()).recentForUser(u, 20);
    }

    @Test
    void mySummary_shouldReturnItemsMap() {
        Utilisateur u = buildUser();
        List<HistoryRecord> list = List.of(record(1L, "coinflip"), record(2L, "roulette"));

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));
        when(historyService.recentForUser(u, 15))
                .thenReturn(list);

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = historyController.mySummary(15, auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("items");
        assertThat(body.get("items")).isSameAs(list);
    }

    @Test
    void deleteMyHistory_shouldDeleteAndReturnNoContent() {
        Utilisateur u = buildUser();

        when(utilisateurRepo.findByEmail("user@example.com"))
                .thenReturn(Optional.of(u));

        Authentication auth = authFor("user@example.com");

        ResponseEntity<?> response = historyController.deleteMyHistory(auth);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        verify(historyService).deleteAllForUser(u);
    }
}
