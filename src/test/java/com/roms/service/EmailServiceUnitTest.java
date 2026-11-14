package com.roms.service;

import com.roms.service.impl.EmailServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailServiceUnitTest {
    @Test @DisplayName("Email: send OTP with recipient sender and expiry instructions using mocked SMTP")
    void sendsOtpMessage() {
        var sender = mock(JavaMailSender.class);
        var service = new EmailServiceImpl(sender,"noreply@roms.test");
        service.sendOtpEmail("staff@roms.test","012345");
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("staff@roms.test");
        assertThat(message.getValue().getFrom()).isEqualTo("noreply@roms.test");
        assertThat(message.getValue().getText()).contains("012345","5 phút");
    }
    @Test @DisplayName("Email: absent SMTP configuration does not send or crash the recovery flow")
    void missingConfiguration() {
        var sender = mock(JavaMailSender.class);
        assertThatCode(() -> new EmailServiceImpl(sender," ").sendOtpEmail("staff@roms.test","012345")).doesNotThrowAnyException();
        assertThatCode(() -> new EmailServiceImpl(null,"").sendOtpEmail("staff@roms.test","012345")).doesNotThrowAnyException();
        verifyNoInteractions(sender);
    }
    @Test @DisplayName("Email: SMTP failure is contained according to the current service contract")
    void smtpFailure() {
        var sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Simulated SMTP failure")).when(sender).send(any(SimpleMailMessage.class));
        var service = new EmailServiceImpl(sender,"noreply@roms.test");
        assertThatCode(() -> service.sendOtpEmail("staff@roms.test","012345")).doesNotThrowAnyException();
        verify(sender).send(any(SimpleMailMessage.class));
    }
}
