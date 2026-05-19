package com.rentcar.api;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.provider.StripePaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StripePaymentProvider MVP stub.
 *
 * StripePaymentProvider is annotated @Profile("prod") so it is never loaded in
 * dev/test Spring context. These tests instantiate it directly to verify the
 * safe-failure contract without needing a Spring application context.
 */
@ExtendWith(MockitoExtension.class)
class StripePaymentProviderTest {

    @Mock
    private Payment payment;

    private StripePaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new StripePaymentProvider();
    }

    // ── pay() ─────────────────────────────────────────────────────────────────

    @Test
    void pay_throwsUnsupportedOperationException_notNull() {
        // Verifies pay() never returns null — it throws before any return statement.
        assertThatThrownBy(() -> provider.pay(payment, "pm_test_123"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pay_exceptionMessageMentionsStripe() {
        assertThatThrownBy(() -> provider.pay(payment, "pm_test_123"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Stripe");
    }

    @Test
    void pay_exceptionMessageIsStable() {
        // Message must be deterministic — callers and ops runbooks can depend on it.
        assertThatThrownBy(() -> provider.pay(payment, "pm_test_123"))
                .hasMessage(StripePaymentProvider.NOT_IMPLEMENTED_MSG);
    }

    // ── refund() ──────────────────────────────────────────────────────────────

    @Test
    void refund_throwsUnsupportedOperationException_notNull() {
        assertThatThrownBy(() -> provider.refund(payment))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refund_exceptionMessageIsStable() {
        assertThatThrownBy(() -> provider.refund(payment))
                .hasMessage(StripePaymentProvider.NOT_IMPLEMENTED_MSG);
    }
}
