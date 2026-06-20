package com.rentcar.api.service;

import com.rentcar.api.domain.auth.EmailOtpCode;
import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.domain.user.AppRole;
import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.domain.user.AuthProvider;
import com.rentcar.api.dto.auth.CompleteEmailProfileRequest;
import com.rentcar.api.email.EmailService;
import com.rentcar.api.email.LoginOtpEmailData;
import com.rentcar.api.repository.AppUserRepository;
import com.rentcar.api.repository.CustomerRepository;
import com.rentcar.api.repository.EmailOtpCodeRepository;
import com.rentcar.api.util.LanguageNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailOtpAuthService {

    private static final int CODE_TTL_MINUTES = 10;
    private static final int PROFILE_TOKEN_TTL_MINUTES = 30;
    private static final int MAX_ATTEMPTS = 5;

    private final EmailOtpCodeRepository otpRepository;
    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final CustomerNumberGenerator customerNumberGenerator;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public void requestCode(String rawEmail, String language) {
        String email = normalizeEmail(rawEmail);
        otpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
                .ifPresent(existing -> existing.setConsumedAt(Instant.now()));

        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        EmailOtpCode otp = EmailOtpCode.builder()
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(Instant.now().plusSeconds(CODE_TTL_MINUTES * 60L))
                .attempts(0)
                .build();
        otpRepository.save(otp);
        emailService.sendLoginOtp(new LoginOtpEmailData(
                email,
                code,
                CODE_TTL_MINUTES,
                LanguageNormalizer.normalizeOrDefault(language)));
    }

    @Transactional
    public VerifyResult verifyCode(String rawEmail, String code) {
        String email = normalizeEmail(rawEmail);
        if (code == null || !code.matches("\\d{6}")) {
            throw invalidCode();
        }

        EmailOtpCode otp = otpRepository.findTopByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(this::invalidCode);

        Instant now = Instant.now();
        if (otp.getExpiresAt().isBefore(now)) {
            otp.setConsumedAt(now);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CODE_EXPIRED");
        }
        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            otp.setConsumedAt(now);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= MAX_ATTEMPTS) {
                otp.setConsumedAt(now);
            }
            throw invalidCode();
        }

        otp.setConsumedAt(now);

        AppUser user = findOrCreateUserForExistingCustomer(email);
        if (user != null && isProfileComplete(user)) {
            return VerifyResult.loggedIn(user);
        }

        String profileToken = newProfileToken();
        otp.setProfileTokenHash(hashToken(profileToken));
        otp.setProfileTokenExpiresAt(now.plusSeconds(PROFILE_TOKEN_TTL_MINUTES * 60L));
        return VerifyResult.profileRequired(profileToken);
    }

    @Transactional
    public AppUser completeProfile(CompleteEmailProfileRequest request) {
        if (request == null || request.profileToken() == null || request.profileToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_TOKEN");
        }
        EmailOtpCode otp = otpRepository.findByProfileTokenHashAndProfileCompletedAtIsNull(hashToken(request.profileToken()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_TOKEN"));
        if (otp.getProfileTokenExpiresAt() == null || otp.getProfileTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PROFILE_TOKEN_EXPIRED");
        }

        String firstName = requiredTrim(request.firstName(), "firstName");
        String lastName = requiredTrim(request.lastName(), "lastName");
        String phoneCountryCode = normalizePhoneCountryCode(request.phoneCountryCode());
        String phoneNumber = normalizePhoneNumber(request.phoneNumber());
        String fullPhone = phoneCountryCode + phoneNumber;

        AppUser user = userRepository.findByEmail(otp.getEmail()).orElseGet(() -> AppUser.builder()
                .email(otp.getEmail())
                .provider(AuthProvider.LOCAL)
                .role(AppRole.CUSTOMER)
                .customerNumber(customerNumberGenerator.nextCustomerNumber())
                .build());

        if (user.getRole() != AppRole.CUSTOMER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhoneCountryCode(phoneCountryCode);
        user.setPhoneNumber(phoneNumber);
        user.setProfileComplete(true);
        AppUser saved = userRepository.save(user);

        customerService.getOrCreateCustomer(firstName + " " + lastName, otp.getEmail(), fullPhone);
        otp.setProfileCompletedAt(Instant.now());
        return saved;
    }

    public String normalizeEmail(String rawEmail) {
        if (rawEmail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        }
        return email;
    }

    public boolean isProfileComplete(AppUser user) {
        return user != null
                && notBlank(user.getFirstName())
                && notBlank(user.getLastName())
                && notBlank(user.getPhoneCountryCode())
                && notBlank(user.getPhoneNumber());
    }

    public String normalizePhoneCountryCode(String raw) {
        String value = requiredTrim(raw, "phoneCountryCode").replace(" ", "");
        if (!value.matches("^\\+[1-9]\\d{0,3}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PHONE_COUNTRY_CODE");
        }
        return value;
    }

    public String normalizePhoneNumber(String raw) {
        String value = requiredTrim(raw, "phoneNumber").replaceAll("\\s+", "");
        if (!value.matches("^[0-9]{4,20}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER");
        }
        return value;
    }

    private AppUser findOrCreateUserForExistingCustomer(String email) {
        var maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isPresent()) {
            return maybeUser.get();
        }

        return customerRepository.findByEmail(email)
                .map(customer -> userRepository.save(userFromCustomer(customer)))
                .orElse(null);
    }

    private AppUser userFromCustomer(Customer customer) {
        String fullName = customer.getFullName() == null ? "" : customer.getFullName().trim();
        String first = fullName;
        String last = "";
        int splitAt = fullName.lastIndexOf(' ');
        if (splitAt > 0) {
            first = fullName.substring(0, splitAt).trim();
            last = fullName.substring(splitAt + 1).trim();
        }
        String[] phoneParts = splitLegacyPhone(customer.getPhone());
        return AppUser.builder()
                .email(customer.getEmail())
                .firstName(first)
                .lastName(last)
                .phoneCountryCode(phoneParts[0])
                .phoneNumber(phoneParts[1])
                .provider(AuthProvider.LOCAL)
                .role(AppRole.CUSTOMER)
                .customerNumber(customerNumberGenerator.nextCustomerNumber())
                .profileComplete(notBlank(first) && notBlank(last) && notBlank(phoneParts[0]) && notBlank(phoneParts[1]))
                .build();
    }

    private String[] splitLegacyPhone(String rawPhone) {
        String phone = rawPhone == null ? "" : rawPhone.replaceAll("\\s+", "");
        for (String code : new String[]{"+34", "+90", "+44", "+33", "+49", "+39", "+1", "+31", "+32", "+351"}) {
            if (phone.startsWith(code) && phone.length() > code.length()) {
                return new String[]{code, phone.substring(code.length())};
            }
        }
        if (phone.matches("^[0-9]{4,20}$")) {
            return new String[]{"+34", phone};
        }
        return new String[]{null, null};
    }

    private String requiredTrim(String value, String field) {
        if (value == null || value.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MISSING_" + field);
        }
        String trimmed = value.trim();
        if (trimmed.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_" + field);
        }
        return trimmed;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private ResponseStatusException invalidCode() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CODE");
    }

    private String newProfileToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash profile token", e);
        }
    }

    public record VerifyResult(String status, AppUser user, String profileToken) {
        static VerifyResult loggedIn(AppUser user) {
            return new VerifyResult("LOGGED_IN", user, null);
        }

        static VerifyResult profileRequired(String profileToken) {
            return new VerifyResult("PROFILE_REQUIRED", null, profileToken);
        }
    }
}
