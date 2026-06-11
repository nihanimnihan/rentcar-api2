package com.rentcar.api.controller;

import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.dto.AuthUserResponse;
import com.rentcar.api.dto.UpdateProfileRequest;
import com.rentcar.api.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    private final AppUserRepository userRepository;

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
        AuthUserResponse resp = new AuthUserResponse(u.getEmail(), u.getFirstName(), u.getLastName(), u.getCountry(), u.isProfileComplete(), u.getRole().name());
        return ResponseEntity.ok().body(resp);
    }

    @GetMapping("/oauth2/authorize")
    public void oauthAuthorize(@RequestParam(name = "returnTo", required = false) String returnTo,
                               @RequestParam(name = "provider", defaultValue = "google") String provider,
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
        request.getSession(true).setAttribute("OAUTH2_RETURN_TO", returnTo);
        // Use fixed provider path to avoid building from arbitrary input
        response.sendRedirect("/oauth2/authorization/google");
    }

    @PostMapping("/api/auth/profile")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest req, HttpServletRequest request) {
        String email = (String) request.getSession().getAttribute("APP_USER_EMAIL");
        if (email == null) return ResponseEntity.status(401).build();

        AppUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();

        // Ensure only CUSTOMER users may update their profile here
        if (user.getRole() != com.rentcar.api.domain.user.AppRole.CUSTOMER) {
            return ResponseEntity.status(403).body(java.util.Map.of("error", "Forbidden"));
        }

        if (req.getFirstName() == null || req.getFirstName().isBlank() || req.getLastName() == null || req.getLastName().isBlank() || req.getCountry() == null || req.getCountry().isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Missing required fields"));
        }

        user.setFirstName(req.getFirstName().trim());
        user.setLastName(req.getLastName().trim());
        user.setCountry(req.getCountry().trim());
        user.setProfileComplete(true);
        userRepository.save(user);

        return ResponseEntity.ok().body(new AuthUserResponse(user.getEmail(), user.getFirstName(), user.getLastName(), user.getCountry(), user.isProfileComplete(), user.getRole().name()));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        try {
            request.getSession().invalidate();
        } catch (Exception ignored) {}
        return ResponseEntity.ok().build();
    }
}
