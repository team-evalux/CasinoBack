package org.example.controller;

import org.example.model.Utilisateur;
import org.example.model.Wallet;
import org.example.repo.UtilisateurRepository;
import org.example.repo.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // H2 en mémoire + schema auto
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:casinotest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",

        // Secret JWT factice mais valide (base64 d'une clé de 32 octets)
        "jwt.secret=ZmFrZXRlc3RqYXZhc2VjcmV0a2V5ZmFrZXRlc3QxMjM0",
        "jwt.expiration-ms=3600000",

        // pour éviter que Spring Mail essaye de faire des trucs exotiques
        "spring.mail.host=localhost",
        "spring.mail.port=2525"
})
class BonusControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UtilisateurRepository utilisateurRepo;

    @Autowired
    private WalletRepository walletRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        walletRepo.deleteAll();
        utilisateurRepo.deleteAll();

        // on crée un utilisateur "réaliste" comme en prod
        Utilisateur u = Utilisateur.builder()
                .email("user@example.com")
                .pseudo("User")
                .motDePasseHash(passwordEncoder.encode("secret"))
                .active(true)
                .role("USER")
                .lastBonusClaim(null) // jamais réclamé
                .build();

        utilisateurRepo.save(u);
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void status_shouldReturnCanClaimTrue_forNewUser() throws Exception {
        mockMvc.perform(get("/api/bonus/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClaim").value(true))
                .andExpect(jsonPath("$.amount").value(1000));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void claim_shouldCreditWalletAndThenStatusCanClaimFalse() throws Exception {
        // au départ : pas de wallet (il sera créé par WalletService)
        assertThat(walletRepo.findAll()).isEmpty();

        // 1) on appelle /api/bonus/claim
        mockMvc.perform(post("/api/bonus/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClaim").value(false))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.solde").value(1000)); // wallet créé à 1000

        // 2) on vérifie en base que le wallet existe et a bien 1000
        Utilisateur u = utilisateurRepo.findByEmail("user@example.com").orElseThrow();
        Wallet w = walletRepo.findByUtilisateur(u).orElseThrow();
        assertThat(w.getSolde()).isEqualTo(1000L);

        // 3) le status doit maintenant renvoyer canClaim=false
        mockMvc.perform(get("/api/bonus/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canClaim").value(false));
    }
}
