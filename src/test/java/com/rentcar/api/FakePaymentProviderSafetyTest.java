package com.rentcar.api;

import com.rentcar.api.payment.provider.FakePaymentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakePaymentProviderSafetyTest {

    @Test
    void fakePaymentProvider_rejectsProdProfileIfEverActivated() {
        FakePaymentProvider provider = new FakePaymentProvider();
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> provider.setEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test profile");
    }

    @Test
    void fakePaymentProvider_rejectsDevProfileIfEverActivated() {
        FakePaymentProvider provider = new FakePaymentProvider();
        MockEnvironment environment = new MockEnvironment().withProperty("spring.profiles.active", "dev");
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> provider.setEnvironment(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test profile");
    }
}
