package org.example.service;

import org.example.model.VerificationCode;
import org.example.repo.VerificationCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private VerificationCodeRepository repo;

    @Mock
    private MailService mailService;

    @InjectMocks
    private VerificationService verificationService;

    // --- envoyerCode() ---

    @Test
    void envoyerCode_shouldDeleteOldCodesSaveNewOneAndSendMail_forRegister() {
        String email = "test@example.com";
        VerificationCode.Type type = VerificationCode.Type.REGISTER;

        // ACT
        verificationService.envoyerCode(email, type);

        // ASSERT : suppression ancien code
        verify(repo).deleteByEmail(email);

        // ASSERT : sauvegarde du nouveau code
        ArgumentCaptor<VerificationCode> codeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(repo).save(codeCaptor.capture());
        VerificationCode saved = codeCaptor.getValue();

        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getType()).isEqualTo(type);
        assertThat(saved.getCode()).hasSize(4).matches("\\d{4}");

        LocalDateTime now = LocalDateTime.now();
        // expiration ≈ maintenant + 10 minutes (on tolère un peu)
        assertThat(saved.getExpiration())
                .isAfter(now.plusMinutes(9))
                .isBefore(now.plusMinutes(11));

        // ASSERT : mail envoyé avec bon sujet et contenu cohérent
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

        verify(mailService).envoyer(toCaptor.capture(), subjectCaptor.capture(), contentCaptor.capture());

        assertThat(toCaptor.getValue()).isEqualTo(email);
        assertThat(subjectCaptor.getValue()).isEqualTo("Vérification de votre adresse e-mail");
        assertThat(contentCaptor.getValue())
                .contains(saved.getCode())
                .contains("10 minutes");
    }

    @Test
    void envoyerCode_shouldUseResetPasswordSubject_forResetPasswordType() {
        String email = "reset@example.com";
        VerificationCode.Type type = VerificationCode.Type.RESET_PASSWORD;

        verificationService.envoyerCode(email, type);

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).envoyer(eq(email), subjectCaptor.capture(), anyString());

        assertThat(subjectCaptor.getValue()).isEqualTo("Code de réinitialisation de mot de passe");
    }

    // --- verifierCode() ---

    @Test
    void verifierCode_shouldReturnTrue_whenCodeMatchesAndNotExpired() {
        String email = "user@example.com";
        String code = "1234";
        VerificationCode.Type type = VerificationCode.Type.REGISTER;

        VerificationCode vc = VerificationCode.builder()
                .email(email)
                .code(code)
                .type(type)
                .expiration(LocalDateTime.now().plusMinutes(5))
                .build();

        when(repo.findTopByEmailAndTypeOrderByExpirationDesc(email, type))
                .thenReturn(Optional.of(vc));

        boolean result = verificationService.verifierCode(email, code, type);

        assertThat(result).isTrue();
    }

    @Test
    void verifierCode_shouldReturnFalse_whenCodeExpired() {
        String email = "user@example.com";
        String code = "1234";
        VerificationCode.Type type = VerificationCode.Type.REGISTER;

        VerificationCode vc = VerificationCode.builder()
                .email(email)
                .code(code)
                .type(type)
                .expiration(LocalDateTime.now().minusMinutes(1)) // déjà expiré
                .build();

        when(repo.findTopByEmailAndTypeOrderByExpirationDesc(email, type))
                .thenReturn(Optional.of(vc));

        boolean result = verificationService.verifierCode(email, code, type);

        assertThat(result).isFalse();
    }

    @Test
    void verifierCode_shouldReturnFalse_whenCodeDoesNotMatch() {
        String email = "user@example.com";
        VerificationCode.Type type = VerificationCode.Type.REGISTER;

        VerificationCode vc = VerificationCode.builder()
                .email(email)
                .code("9999")
                .type(type)
                .expiration(LocalDateTime.now().plusMinutes(5))
                .build();

        when(repo.findTopByEmailAndTypeOrderByExpirationDesc(email, type))
                .thenReturn(Optional.of(vc));

        boolean result = verificationService.verifierCode(email, "1234", type);

        assertThat(result).isFalse();
    }

    @Test
    void verifierCode_shouldReturnFalse_whenNoCodeFound() {
        String email = "user@example.com";
        VerificationCode.Type type = VerificationCode.Type.REGISTER;

        when(repo.findTopByEmailAndTypeOrderByExpirationDesc(email, type))
                .thenReturn(Optional.empty());

        boolean result = verificationService.verifierCode(email, "1234", type);

        assertThat(result).isFalse();
    }

    // --- supprimerCodesExpirés() ---

    @Test
    void supprimerCodesExpirés_shouldCallRepositoryDeleteByExpirationBeforeNow() {
        verificationService.supprimerCodesExpirés();

        verify(repo).deleteByExpirationBefore(any(LocalDateTime.class));
    }
}
