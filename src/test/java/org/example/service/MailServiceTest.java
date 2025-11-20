// src/test/java/org/example/service/MailServiceTest.java
package org.example.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private MailService mailService;

    @Test
    void envoyer_shouldBuildAndSendSimpleMailMessage() {
        String to = "user@example.com";
        String sujet = "Sujet";
        String contenu = "Contenu du mail";

        mailService.envoyer(to, sujet, contenu);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly(to);
        assertThat(sent.getSubject()).isEqualTo(sujet);
        assertThat(sent.getText()).isEqualTo(contenu);
    }
}
