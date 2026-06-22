package com.rentcar.api;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.exception.PaymentProviderNotConfiguredException;
import com.rentcar.api.payment.provider.StripePaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StripePaymentProvider MVP stub.
 *
 * <p>These tests instantiate it directly to verify the safe-failure contract when
 * Stripe configuration is missing:
 * all operations throw {@link PaymentProviderNotConfiguredException} (→ 503), never a raw
 * {@link UnsupportedOperationException} that would produce a generic 500.
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
    void pay_throwsDomainException_neverNull() {
        assertThatThrownBy(() -> provider.pay(payment, "pm_test_123"))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    void pay_exceptionMessageMentionsStripe() {
        assertThatThrownBy(() -> provider.pay(payment, "pm_test_123"))
                .isInstanceOf(PaymentProviderNotConfiguredException.class)
                .hasMessageContaining("Stripe");
    }

    // ── refund() ──────────────────────────────────────────────────────────────

    @Test
    void refund_throwsDomainException_neverNull() {
        assertThatThrownBy(() -> provider.refund(payment))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    void refund_exceptionMessageMentionsStripe() {
        assertThatThrownBy(() -> provider.refund(payment))
                .isInstanceOf(PaymentProviderNotConfiguredException.class)
                .hasMessageContaining("Stripe");
    }

    // ── createIntent() ────────────────────────────────────────────────────────

    @Test
    void createIntent_throwsDomainException_neverNull() {
        assertThatThrownBy(() -> provider.createIntent(payment))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);
    }

    @Test
    void createIntent_exceptionMessageMentionsStripe() {
        assertThatThrownBy(() -> provider.createIntent(payment))
                .isInstanceOf(PaymentProviderNotConfiguredException.class)
                .hasMessageContaining("Stripe");
    }

    // ── providerName() ────────────────────────────────────────────────────────

    @Test
    void providerName_returnsStripe() {
        assertThat(provider.providerName()).isEqualTo("STRIPE");
    }
}
