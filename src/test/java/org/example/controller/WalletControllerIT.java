package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Utilisateur;
import org.example.repo.UtilisateurRepository;
import org.example.repo.VerificationCodeRepository;
import org.example.repo.WalletRepository;
import org.example.service.GameHistoryService;
import org.example.service.MailService;
import org.example.service.UtilisateurService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;


import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "jwt.secret=VGhpcy1pcy1hLXRlc3QtandrLXNlY3JldC1rZXktMTIzNDU2Nzg=",
        "app.cors.allowed-origins=http://localhost:4200"
})
class WalletControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MailService mailService;

    @MockBean
    private JavaMailSender mailSender;


    @Autowired
    private UtilisateurService utilisateurService;

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private GameHistoryService gameHistoryRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String token; // JWT pour l’utilisateur de test

    private static final String EMAIL = "wallet-it@test.com";
    private static final String PSEUDO = "walletUser";
    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() throws Exception {
        // On repart sur une base propre à chaque test
        Utilisateur u = null;
        gameHistoryRepository.deleteAllForUser(u);
        walletRepository.deleteAll();
        verificationCodeRepository.deleteAll();
        utilisateurRepository.deleteAll();

        // Création d’un utilisateur avec wallet initial = 1000 (via UtilisateurService.inscrire)
        u = utilisateurService.inscrire(EMAIL, PSEUDO, PASSWORD);

        // Récupération d’un vrai JWT via /api/auth/login
        this.token = loginAndGetToken(EMAIL, PASSWORD);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        // On construit le JSON "à la main" pour coller à ton AuthRequest
        String json = String.format(
                "{\"email\":\"%s\",\"motDePasse\":\"%s\"}",
                email, password
        );

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        // On lit la réponse comme un simple Map<String, Object>
        Map<String, Object> resp = objectMapper.readValue(
                body,
                new TypeReference<Map<String, Object>>() {}
        );

        return (String) resp.get("token");
    }


    @Test
    void solde_me_shouldReturnInitialWallet() throws Exception {
        mockMvc.perform(get("/api/wallet/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solde").value(1000));
    }

    @Test
    void credit_shouldIncreaseSolde() throws Exception {
        mockMvc.perform(post("/api/wallet/credit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solde").value(1500));
    }

    @Test
    void debit_shouldDecreaseSolde() throws Exception {
        mockMvc.perform(post("/api/wallet/debit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":400}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solde").value(600));
    }

    @Test
    void debit_shouldReturnBadRequest_whenInsufficientFunds() throws Exception {
        mockMvc.perform(post("/api/wallet/debit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"montant\":999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Solde insuffisant"));
    }

    @Test
    void accessWithoutToken_shouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/wallet/me"))
                .andExpect(status().isForbidden());
    }
}
