package com.rentcar.api;

import com.rentcar.api.email.EmailService;
import com.rentcar.api.email.SmtpEmailService;
import com.rentcar.api.payment.provider.PaymentProvider;
import com.rentcar.api.payment.provider.StripePaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=1025",
        "rentcar.email.from=no-reply@rentcar.local"
})
@ActiveProfiles("dev")
class LocalSmtpEmailProfileTest {

    @Autowired
    private EmailService emailService;

    @Autowired
    private PaymentProvider paymentProvider;

    @Test
    void devWithSmtpHostUsesSmtpEmailServiceAndStripeProvider() {
        assertThat(emailService).isInstanceOf(SmtpEmailService.class);
        assertThat(paymentProvider).isInstanceOf(StripePaymentProvider.class);
    }
}
