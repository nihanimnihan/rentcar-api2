package com.rentcar.api.config;

import com.rentcar.api.email.EmailService;
import com.rentcar.api.payment.provider.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupProfileLogger implements ApplicationRunner {

    private final Environment environment;
    private final PaymentProvider paymentProvider;
    private final EmailService emailService;

    @Value("${stripe.api-key:}")
    private String stripeApiKey;

    @Override
    public void run(ApplicationArguments args) {
        String profiles = Arrays.toString(environment.getActiveProfiles());
        boolean stripeConfigured = stripeApiKey != null && !stripeApiKey.isBlank();

        log.info("Startup profiles: activeProfiles={}", profiles);
        log.info("Startup payment provider: name={}", paymentProvider.providerName());
        log.info("Startup Stripe configuration: apiKeyConfigured={}", stripeConfigured);
        log.info("Startup email provider: name={}", emailService.getClass().getSimpleName());
    }
}
