package com.rentcar.api.controller;

import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.dto.AuthUserResponse;
import com.rentcar.api.dto.UpdateProfileRequest;
import com.rentcar.api.dto.auth.CompleteEmailProfileRequest;
import com.rentcar.api.dto.auth.EmailAuthResponse;
import com.rentcar.api.dto.auth.EmailCodeRequest;
import com.rentcar.api.dto.auth.VerifyEmailCodeRequest;
import com.rentcar.api.repository.AppUserRepository;
import com.rentcar.api.service.CustomerService;
import com.rentcar.api.service.EmailOtpAuthService;
import com.rentcar.api.service.CustomerNumberGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AppUserRepository userRepository;
    private final CustomerNumberGenerator customerNumberGenerator;
    private final EmailOtpAuthService emailOtpAuthService;
    private final CustomerService customerService;

    private boolean isSafeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) return false;
        // Must start with single slash and not // (protocol-relative)
        if (!returnTo.startsWith("/")) return false;
        if (returnTo.startsWith("//")) return false;
        // Reject javascript: URIs and similar schemes
        try {
            URI uri = new URI(returnTo);
            return (uri.getScheme() == null && uri.getHost() == null);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        String email = (String) request.getSession().getAttribute("APP_USER_EMAIL");
        if (email == null) {
            return ResponseEntity.ok().body(java.util.Map.of("authenticated", false));
        }
        var maybe = userRepository.findByEmail(email);
        if (maybe.isEmpty()) {
            return ResponseEntity.ok().body(java.util.Map.of("authenticated", false));
        }
        AppUser u = maybe.get();
        // Ensure customerNumber exists and persist if missing
        if (u.getCustomerNumber() == null || u.getCustomerNumber().isBlank()) {
            String generated = customerNumberGenerator.nextCustomerNumber();
            u.setCustomerNumber(generated);
            userRepository.save(u);
            log.info("Generated customerNumber for user {}: {}", u.getEmail(), generated);
        }
        AuthUserResponse resp = responseFor(u);
        return ResponseEntity.ok().body(resp);
    }

    @PostMapping("/api/auth/email/request-code")
    public ResponseEntity<?> requestEmailCode(@RequestBody EmailCodeRequest req, HttpServletRequest request) {
        String language = request.getHeader("Accept-Language");
        emailOtpAuthService.requestCode(req.email(), language);
        return ResponseEntity.ok(EmailAuthResponse.codeSent());
    }

    @PostMapping("/api/auth/email/verify-code")
    public ResponseEntity<?> verifyEmailCode(@RequestBody VerifyEmailCodeRequest req, HttpServletRequest request) {
        var result = emailOtpAuthService.verifyCode(req.email(), req.code());
        if ("LOGGED_IN".equals(result.status())) {
            request.getSession(true).setAttribute("APP_USER_EMAIL", result.user().getEmail());
            request.getSession(true).setAttribute("EMAIL_VERIFIED", Boolean.TRUE);
            return ResponseEntity.ok(EmailAuthResponse.loggedIn());
        }
        return ResponseEntity.ok(EmailAuthResponse.profileRequired(result.profileToken()));
    }

    @PostMapping("/api/auth/email/complete-profile")
    public ResponseEntity<?> completeEmailProfile(@RequestBody CompleteEmailProfileRequest req, HttpServletRequest request) {
        AppUser user = emailOtpAuthService.completeProfile(req);
        request.getSession(true).setAttribute("APP_USER_EMAIL", user.getEmail());
        request.getSession(true).setAttribute("EMAIL_VERIFIED", Boolean.TRUE);
        return ResponseEntity.ok(EmailAuthResponse.loggedIn());
    }

    @GetMapping("/oauth2/authorize")
    public void oauthAuthorize(@RequestParam(name = "returnTo", required = false) String returnTo,
                               @RequestParam(name = "provider", defaultValue = "google") String provider,
                               @RequestParam(name = "popup", required = false) String popup,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        // Only allow google provider for customer OAuth flows
        if (provider == null || !"google".equalsIgnoreCase(provider)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid provider");
            return;
        }

        if (!isSafeReturnTo(returnTo)) {
            returnTo = request.getContextPath() + "/index.html";
        }
        var session = request.getSession(true);
        session.setAttribute("OAUTH2_RETURN_TO", returnTo);
        // If popup parameter is present and truthy, remember to signal success handler to return a popup callback page
        if (popup != null && ("1".equals(popup) || "true".equalsIgnoreCase(popup))) {
            session.setAttribute("OAUTH2_POPUP", Boolean.TRUE);
            org.slf4j.LoggerFactory.getLogger(AuthController.class).info("OAuth authorize requested in popup mode, sessionId={}", session.getId());
        } else {
            session.removeAttribute("OAUTH2_POPUP");
            org.slf4j.LoggerFactory.getLogger(AuthController.class).info("OAuth authorize requested, returnTo={}, sessionId={}", returnTo, session.getId());
        }
        // Use fixed provider path to avoid building from arbitrary input
        response.sendRedirect("/oauth2/authorization/google");
    }

    @PostMapping("/api/auth/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest req, HttpServletRequest request) {
        String email = (String) request.getSession().getAttribute("APP_USER_EMAIL");
        if (email == null) {
            org.slf4j.LoggerFactory.getLogger(AuthController.class).warn("Profile update denied: no session");
            return ResponseEntity.status(401).build();
        }

        AppUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            org.slf4j.LoggerFactory.getLogger(AuthController.class).warn("Profile update failed: user not found email={}", email);
            return ResponseEntity.status(404).build();
        }

        // Ensure only CUSTOMER users may update their profile here
        if (user.getRole() != com.rentcar.api.domain.user.AppRole.CUSTOMER) {
            org.slf4j.LoggerFactory.getLogger(AuthController.class).warn("Profile update forbidden for non-customer email={}", email);
            return ResponseEntity.status(403).body(java.util.Map.of("error", "Forbidden"));
        }

        java.util.List<String> missing = new java.util.ArrayList<>();
        if (req.getFirstName() == null || req.getFirstName().isBlank()) missing.add("firstName");
        if (req.getLastName() == null || req.getLastName().isBlank()) missing.add("lastName");
        if (req.getPhoneCountryCode() == null || req.getPhoneCountryCode().isBlank()) missing.add("phoneCountryCode");
        if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank()) missing.add("phoneNumber");
        if (!missing.isEmpty()) {
            log.warn("Profile update bad request email={} missingFields={}", email, missing);
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "VALIDATION_ERROR",
                    "message", "Missing required fields",
                    "fields", missing
            ));
        }

        String phoneCountryCode = emailOtpAuthService.normalizePhoneCountryCode(req.getPhoneCountryCode());
        String phoneNumber = emailOtpAuthService.normalizePhoneNumber(req.getPhoneNumber());
        user.setFirstName(req.getFirstName().trim());
        user.setLastName(req.getLastName().trim());
        if (req.getCountry() != null && !req.getCountry().isBlank()) {
            user.setCountry(req.getCountry().trim());
        }
        user.setPhoneCountryCode(phoneCountryCode);
        user.setPhoneNumber(phoneNumber);
        user.setProfileComplete(true);
        userRepository.save(user);
        customerService.getOrCreateCustomer(
                user.getFirstName() + " " + user.getLastName(),
                user.getEmail(),
                phoneCountryCode + phoneNumber);
        log.info("Profile updated for email={}, sessionId={}", email, request.getSession(false) != null ? request.getSession(false).getId() : "-");

        return ResponseEntity.ok().body(responseFor(user));
    }

    private AuthUserResponse responseFor(AppUser user) {
        return new AuthUserResponse(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCountry(),
                user.getPhoneCountryCode(),
                user.getPhoneNumber(),
                user.isProfileComplete(),
                user.getRole().name(),
                user.getCustomerNumber());
    }
}
