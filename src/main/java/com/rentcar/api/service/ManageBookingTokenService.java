package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class ManageBookingTokenService {

    public static final String INVALID_OR_EXPIRED_MESSAGE =
            "This secure booking link is invalid or expired. Please look up your booking with your reference and last name.";

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final BookingRepository bookingRepository;
    private final AppClock appClock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder tokenEncoder = Base64.getUrlEncoder().withoutPadding();

    @Value("${rentcar.manage-booking-token.ttl-days:30}")
    private long ttlDays;

    /**
     * Issues a fresh manage-booking link token and stores only its SHA-256 hash.
     *
     * <p>Because the raw token is never stored, later transactional emails rotate
     * this token instead of reusing the previous raw value.
     */
    @Transactional
    public String issueToken(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking must be persisted before issuing a manage token");
        }

        Instant expiresAt = appClock.nowUtc().plus(Duration.ofDays(Math.max(ttlDays, 1)));
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String token = generateRawToken();
            String tokenHash = hashToken(token);
            if (!bookingRepository.existsByManageTokenHash(tokenHash)) {
                booking.setManageTokenHash(tokenHash);
                booking.setManageTokenExpiresAt(expiresAt);
                booking.setManageTokenRevokedAt(null);
                bookingRepository.save(booking);
                return token;
            }
        }
        throw new IllegalStateException("Failed to generate unique manage booking token");
    }

    @Transactional(readOnly = true)
    public Booking findBookingByToken(String token) {
        String tokenHash = hashTokenOrThrow(token);
        Booking booking = bookingRepository.findByManageTokenHashEager(tokenHash)
                .orElseThrow(() -> new BookingNotFoundException(INVALID_OR_EXPIRED_MESSAGE));
        validateTokenState(booking, tokenHash);
        return booking;
    }

    public void validateTokenForBooking(Booking booking, String token) {
        String tokenHash = hashTokenOrThrow(token);
        validateTokenState(booking, tokenHash);
    }

    @Transactional
    public void revokeToken(Booking booking) {
        if (booking == null) {
            return;
        }
        booking.setManageTokenRevokedAt(appClock.nowUtc());
        bookingRepository.save(booking);
    }

    private void validateTokenState(Booking booking, String tokenHash) {
        String storedHash = booking.getManageTokenHash();
        if (storedHash == null || !constantTimeEquals(storedHash, tokenHash)) {
            throw new BookingNotFoundException(INVALID_OR_EXPIRED_MESSAGE);
        }
        Instant expiresAt = booking.getManageTokenExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(appClock.nowUtc())) {
            throw new BookingNotFoundException(INVALID_OR_EXPIRED_MESSAGE);
        }
        if (booking.getManageTokenRevokedAt() != null) {
            throw new BookingNotFoundException(INVALID_OR_EXPIRED_MESSAGE);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return tokenEncoder.encodeToString(bytes);
    }

    private String hashTokenOrThrow(String token) {
        if (token == null || token.isBlank()) {
            throw new BookingNotFoundException(INVALID_OR_EXPIRED_MESSAGE);
        }
        return hashToken(token.trim());
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
