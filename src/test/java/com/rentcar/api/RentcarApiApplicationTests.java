package com.rentcar.api;

import com.rentcar.api.email.EmailService;
import com.rentcar.api.email.FakeEmailService;
import com.rentcar.api.payment.provider.FakePaymentProvider;
import com.rentcar.api.payment.provider.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RentcarApiApplicationTests {

	@Autowired
	private PaymentProvider paymentProvider;

	@Autowired
	private EmailService emailService;

	@Test
	void contextLoads() {
	}

	@Test
	void testProfileUsesFakePaymentAndFakeEmail() {
		assertThat(paymentProvider).isInstanceOf(FakePaymentProvider.class);
		assertThat(emailService).isInstanceOf(FakeEmailService.class);
	}

}
