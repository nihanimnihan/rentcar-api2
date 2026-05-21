package com.rentcar.api.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates human-friendly booking reference codes in the format RC-YYMMDD-XXXX.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Date part uses the business timezone (Europe/Madrid) to match what the customer sees.</li>
 *   <li>Suffix alphabet excludes visually ambiguous characters O, I, 0, 1.</li>
 *   <li>{@link SecureRandom} provides sufficient entropy — 32^4 ≈ 1 million combinations per day.</li>
 * </ul>
 */
@Component
public class BookingReferenceGenerator {

    /** Uppercase letters + digits, excluding confusable chars: O (looks like 0), I (looks like 1). */
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int SUFFIX_LENGTH = 4;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final BusinessTimezone businessTimezone;
    private final SecureRandom random = new SecureRandom();

    public BookingReferenceGenerator(BusinessTimezone businessTimezone) {
        this.businessTimezone = businessTimezone;
    }

    /** Returns a new reference code, e.g. {@code RC-260521-K8P4}. */
    public String generate() {
        LocalDate today = businessTimezone.todayBusiness();
        String datePart = today.format(DATE_FMT);
        StringBuilder sb = new StringBuilder("RC-").append(datePart).append("-");
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
