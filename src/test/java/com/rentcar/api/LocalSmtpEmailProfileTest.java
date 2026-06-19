package com.rentcar.api;

import com.rentcar.api.email.EmailService;
import com.rentcar.api.email.SmtpEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"dev", "local-smtp"})
class LocalSmtpEmailProfileTest {

    @Autowired
    private EmailService emailService;

    @Test
    void localSmtpProfileUsesSmtpEmailService() {
        assertThat(emailService).isInstanceOf(SmtpEmailService.class);
    }
}
