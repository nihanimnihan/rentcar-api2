package com.rentcar.api.email;

import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.util.LanguageNormalizer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EmailLocalizationService {

    private static final String BRAND_NAME = "Paradise Deluxe";

    public String normalizeLanguage(String language) {
        return LanguageNormalizer.normalizeOrDefault(language);
    }

    public LocalizedEmail bookingConfirmation(ConfirmationEmailData data) {
        String language = normalizeLanguage(data.language());
        return new LocalizedEmail(
                switch (language) {
                    case "tr" -> "Paradise Deluxe rezervasyonunuz onaylandı - " + data.bookingReference();
                    case "es" -> "Reserva Paradise Deluxe confirmada - " + data.bookingReference();
                    default -> "Paradise Deluxe booking confirmed - " + data.bookingReference();
                },
                confirmationBody(data, language)
        );
    }

    public LocalizedEmail bookingCancellation(CancellationEmailData data) {
        String language = normalizeLanguage(data.language());
        return new LocalizedEmail(
                switch (language) {
                    case "tr" -> "Paradise Deluxe rezervasyonunuz iptal edildi - " + data.bookingReference();
                    case "es" -> "Reserva Paradise Deluxe cancelada - " + data.bookingReference();
                    default -> "Paradise Deluxe booking cancelled - " + data.bookingReference();
                },
                cancellationBody(data, language)
        );
    }

    public LocalizedEmail refundCompleted(RefundCompletedEmailData data) {
        String language = normalizeLanguage(data.language());
        return new LocalizedEmail(
                switch (language) {
                    case "tr" -> "Paradise Deluxe iadeniz tamamlandı - " + data.bookingReference();
                    case "es" -> "Reembolso Paradise Deluxe completado - " + data.bookingReference();
                    default -> "Paradise Deluxe refund completed - " + data.bookingReference();
                },
                refundCompletedBody(data, language)
        );
    }

    public LocalizedEmail noShowRecorded(NoShowEmailData data) {
        String language = normalizeLanguage(data.language());
        return new LocalizedEmail(
                switch (language) {
                    case "tr" -> "Paradise Deluxe gelinmeyen rezervasyon kaydı - " + data.bookingReference();
                    case "es" -> "No-show registrado para su reserva Paradise Deluxe - " + data.bookingReference();
                    default -> "Paradise Deluxe no-show recorded - " + data.bookingReference();
                },
                noShowRecordedBody(data, language)
        );
    }

    public LocalizedEmail loginOtp(LoginOtpEmailData data) {
        String language = normalizeLanguage(data.language());
        return new LocalizedEmail(
                switch (language) {
                    case "tr" -> "Paradise Deluxe giriş kodunuz";
                    case "es" -> "Tu código de acceso de Paradise Deluxe";
                    default -> "Your Paradise Deluxe sign-in code";
                },
                switch (language) {
                    case "tr" -> """
                            Paradise Deluxe giriş kodunuz: %s

                            Bu kod %d dakika içinde sona erer.

                            Bu kodu siz istemediyseniz bu e-postayı yok sayabilirsiniz.
                            """.formatted(data.code(), data.expiresInMinutes());
                    case "es" -> """
                            Tu código de acceso de Paradise Deluxe es: %s

                            Este código caduca en %d minutos.

                            Si no solicitaste este código, ignora este correo.
                            """.formatted(data.code(), data.expiresInMinutes());
                    default -> """
                            Your Paradise Deluxe sign-in code is: %s

                            This code expires in %d minutes.

                            If you did not request this code, you can ignore this email.
                            """.formatted(data.code(), data.expiresInMinutes());
                }
        );
    }

    public String refundStatusLabel(PaymentStatus status, String language) {
        String normalized = normalizeLanguage(language);
        if (status == null) {
            return switch (normalized) {
                case "tr" -> "Mevcut değil";
                case "es" -> "No disponible";
                default -> "Not available";
            };
        }

        return switch (normalized) {
            case "tr" -> switch (status) {
                case REFUNDED -> "İade tamamlandı";
                case REFUND_PENDING -> "İade başlatıldı";
                case PAID -> "Ödendi - iade henüz tamamlanmadı";
                case NO_REFUND -> "İade uygulanmaz";
                case PENDING, FAILED, CANCELLED -> "Ödeme alınmadı";
            };
            case "es" -> switch (status) {
                case REFUNDED -> "Reembolso completado";
                case REFUND_PENDING -> "Reembolso iniciado";
                case PAID -> "Pagado - reembolso aún no completado";
                case NO_REFUND -> "Sin reembolso aplicable";
                case PENDING, FAILED, CANCELLED -> "No se ha cobrado ningún cargo";
            };
            default -> switch (status) {
                case REFUNDED -> "Refund completed";
                case REFUND_PENDING -> "Refund initiated";
                case PAID -> "Paid - refund not yet completed";
                case NO_REFUND -> "No refund applies";
                case PENDING, FAILED, CANCELLED -> "No charge collected";
            };
        };
    }

    public String cancellationRefundTimingMessage(String language) {
        return switch (normalizeLanguage(language)) {
            case "tr" -> "İade uygulanıyorsa, tutarın banka hesabınızda görünmesi birkaç iş günü sürebilir.";
            case "es" -> "Si corresponde un reembolso, puede tardar unos días laborables en aparecer en su cuenta bancaria.";
            default -> "If a refund applies, it may take a few business days to appear in your bank account.";
        };
    }

    public String refundCompletedTimingMessage(String language) {
        return switch (normalizeLanguage(language)) {
            case "tr" -> "İade tarafımızda tamamlandı, ancak tutarın banka hesabınızda görünmesi birkaç iş günü sürebilir.";
            case "es" -> "El reembolso se ha completado por nuestra parte, pero puede tardar unos días laborables en aparecer en su cuenta bancaria.";
            default -> "The refund is completed on our side, but it may still take a few business days to appear in your bank account.";
        };
    }

    private String confirmationBody(ConfirmationEmailData data, String language) {
        return switch (language) {
            case "tr" -> """
                    Merhaba %s,

                    Paradise Deluxe rezervasyonunuz onaylandı.

                    Rezervasyon referansı: %s
                    Müşteri adı: %s
                    Alış: %s - %s
                    Teslim: %s - %s
                    Seçilen araç/hizmet: %s
                    Koruma paketi: %s
                    Koruma toplamı: %s EUR
                    Güvence bedeli: %s EUR
                    Toplam fiyat: %s EUR
                    Rezervasyonu yönetin: %s

                    Paradise Deluxe’i tercih ettiğiniz için teşekkür ederiz.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    data.customerName(),
                    data.pickupLocation(),
                    formatDate(data.pickupDateTime(), language),
                    data.dropoffLocation(),
                    formatDate(data.dropoffDateTime(), language),
                    data.selectedService(),
                    optional(data.insuranceName(), notAvailable(language)),
                    money(data.insuranceTotal()),
                    money(data.depositAmount()),
                    money(data.totalPrice()),
                    optional(data.managementUrl(), notAvailable(language)));
            case "es" -> """
                    Hola %s,

                    Su reserva con Paradise Deluxe está confirmada.

                    Referencia de reserva: %s
                    Nombre del cliente: %s
                    Recogida: %s - %s
                    Devolución: %s - %s
                    Coche/servicio seleccionado: %s
                    Paquete de protección: %s
                    Total de protección: EUR %s
                    Depósito: EUR %s
                    Precio total: EUR %s
                    Gestionar reserva: %s

                    Gracias por elegir Paradise Deluxe.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    data.customerName(),
                    data.pickupLocation(),
                    formatDate(data.pickupDateTime(), language),
                    data.dropoffLocation(),
                    formatDate(data.dropoffDateTime(), language),
                    data.selectedService(),
                    optional(data.insuranceName(), notAvailable(language)),
                    money(data.insuranceTotal()),
                    money(data.depositAmount()),
                    money(data.totalPrice()),
                    optional(data.managementUrl(), notAvailable(language)));
            default -> """
                    Hi %s,

                    Your Paradise Deluxe booking is confirmed.

                    Booking reference: %s
                    Customer name: %s
                    Pick-up: %s - %s
                    Drop-off: %s - %s
                    Selected car/service: %s
                    Protection package: %s
                    Protection total: EUR %s
                    Deposit: EUR %s
                    Total price: EUR %s
                    Manage booking: %s

                    Thank you for choosing Paradise Deluxe.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    data.customerName(),
                    data.pickupLocation(),
                    formatDate(data.pickupDateTime(), language),
                    data.dropoffLocation(),
                    formatDate(data.dropoffDateTime(), language),
                    data.selectedService(),
                    optional(data.insuranceName(), notAvailable(language)),
                    money(data.insuranceTotal()),
                    money(data.depositAmount()),
                    money(data.totalPrice()),
                    optional(data.managementUrl(), notAvailable(language)));
        };
    }

    private String cancellationBody(CancellationEmailData data, String language) {
        return switch (language) {
            case "tr" -> """
                    Merhaba %s,

                    Paradise Deluxe rezervasyonunuz iptal edildi.

                    Rezervasyon referansı: %s
                    İptal nedeni: %s
                    İade durumu: %s
                    %s
                    Rezervasyonu yönetin: %s

                    Paradise Deluxe’i tercih ettiğiniz için teşekkür ederiz.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.cancellationReason(), notProvided(language)),
                    refundStatusLabel(data.refundStatus(), language),
                    cancellationRefundTimingMessage(language),
                    optional(data.managementUrl(), notAvailable(language)));
            case "es" -> """
                    Hola %s,

                    Su reserva con Paradise Deluxe ha sido cancelada.

                    Referencia de reserva: %s
                    Motivo de cancelación: %s
                    Estado del reembolso: %s
                    %s
                    Gestionar reserva: %s

                    Gracias por elegir Paradise Deluxe.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.cancellationReason(), notProvided(language)),
                    refundStatusLabel(data.refundStatus(), language),
                    cancellationRefundTimingMessage(language),
                    optional(data.managementUrl(), notAvailable(language)));
            default -> """
                    Hi %s,

                    Your Paradise Deluxe booking has been cancelled.

                    Booking reference: %s
                    Cancellation reason: %s
                    Refund status: %s
                    %s
                    Manage booking: %s

                    Thank you for choosing Paradise Deluxe.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.cancellationReason(), notProvided(language)),
                    refundStatusLabel(data.refundStatus(), language),
                    cancellationRefundTimingMessage(language),
                    optional(data.managementUrl(), notAvailable(language)));
        };
    }

    private String refundCompletedBody(RefundCompletedEmailData data, String language) {
        return switch (language) {
            case "tr" -> """
                    Merhaba %s,

                    Paradise Deluxe rezervasyonunuz için iade tamamlandı.

                    Rezervasyon referansı: %s
                    İade referansı: %s
                    %s
                    Rezervasyonu yönetin: %s

                    Paradise Deluxe’i tercih ettiğiniz için teşekkür ederiz.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.refundReference(), notAvailable(language)),
                    refundCompletedTimingMessage(language),
                    optional(data.managementUrl(), notAvailable(language)));
            case "es" -> """
                    Hola %s,

                    El reembolso de su reserva con Paradise Deluxe se ha completado.

                    Referencia de reserva: %s
                    Referencia de reembolso: %s
                    %s
                    Gestionar reserva: %s

                    Gracias por elegir Paradise Deluxe.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.refundReference(), notAvailable(language)),
                    refundCompletedTimingMessage(language),
                    optional(data.managementUrl(), notAvailable(language)));
            default -> """
                    Hi %s,

                    Your refund for Paradise Deluxe booking %s has been completed.

                    Refund reference: %s
                    %s
                    Manage booking: %s

                    Thank you for choosing Paradise Deluxe.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.refundReference(), notAvailable(language)),
                    refundCompletedTimingMessage(language),
                    optional(data.managementUrl(), notAvailable(language)));
        };
    }

    private String noShowRecordedBody(NoShowEmailData data, String language) {
        return switch (language) {
            case "tr" -> """
                    Merhaba %s,

                    Paradise Deluxe rezervasyonunuz gelinmedi olarak kaydedildi.

                    Rezervasyon referansı: %s
                    İade durumu: İade uygulanmaz
                    Rezervasyonu yönetin: %s

                    Sorularınız varsa lütfen destek ekibimizle iletişime geçin.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.managementUrl(), notAvailable(language)));
            case "es" -> """
                    Hola %s,

                    Su reserva con Paradise Deluxe se ha registrado como no-show.

                    Referencia de reserva: %s
                    Estado del reembolso: Sin reembolso aplicable
                    Gestionar reserva: %s

                    Si tiene alguna pregunta, contacte con nuestro equipo de soporte.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.managementUrl(), notAvailable(language)));
            default -> """
                    Hi %s,

                    Your Paradise Deluxe booking has been recorded as a no-show.

                    Booking reference: %s
                    Refund status: No refund applies
                    Manage booking: %s

                    If you have questions, please contact our support team.
                    """.formatted(
                    data.customerName(),
                    data.bookingReference(),
                    optional(data.managementUrl(), notAvailable(language)));
        };
    }

    private String formatDate(java.time.LocalDateTime value, String language) {
        if (value == null) {
            return notAvailable(language);
        }
        return value.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", locale(language)));
    }

    private Locale locale(String language) {
        return switch (language) {
            case "tr" -> Locale.forLanguageTag("tr-TR");
            case "es" -> Locale.forLanguageTag("es-ES");
            default -> Locale.ENGLISH;
        };
    }

    private String optional(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String notAvailable(String language) {
        return switch (normalizeLanguage(language)) {
            case "tr" -> "Mevcut değil";
            case "es" -> "No disponible";
            default -> "Not available";
        };
    }

    private String notProvided(String language) {
        return switch (normalizeLanguage(language)) {
            case "tr" -> "Belirtilmedi";
            case "es" -> "No indicado";
            default -> "Not provided";
        };
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
