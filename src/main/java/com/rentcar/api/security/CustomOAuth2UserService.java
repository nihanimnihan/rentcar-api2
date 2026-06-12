package com.rentcar.api.security;

import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.domain.user.AppRole;
import com.rentcar.api.domain.user.AuthProvider;
import com.rentcar.api.repository.AppUserRepository;
import com.rentcar.api.service.CustomerNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository userRepository;
    private final CustomerNumberGenerator numberGenerator;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        // Keep default behaviour; persistence handled in success handler
        return oauth2User;
    }

    public AppUser processGoogleUser(Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        if (email == null) throw new IllegalArgumentException("Google account has no email");

        String first = (String) attributes.getOrDefault("given_name", null);
        String last = (String) attributes.getOrDefault("family_name", null);

        var maybe = userRepository.findByEmail(email);
        if (maybe.isPresent()) {
            AppUser existing = maybe.get();
            boolean changed = false;
            // Fill missing first/last if Google provides them, but do not overwrite existing non-empty values
            if ((existing.getFirstName() == null || existing.getFirstName().isBlank()) && first != null && !first.isBlank()) {
                existing.setFirstName(first);
                changed = true;
            }
            if ((existing.getLastName() == null || existing.getLastName().isBlank()) && last != null && !last.isBlank()) {
                existing.setLastName(last);
                changed = true;
            }
            // Do not set country from Google (not reliable). Only keep existing country if set.
            // Recompute profileComplete only if all required fields are present
            boolean profileComplete = (existing.getFirstName() != null && !existing.getFirstName().isBlank())
                    && (existing.getLastName() != null && !existing.getLastName().isBlank())
                    && (existing.getCountry() != null && !existing.getCountry().isBlank());
            if (existing.isProfileComplete() != profileComplete) {
                existing.setProfileComplete(profileComplete);
                changed = true;
            }
            if (changed) {
                existing.setUpdatedAt(Instant.now());
                userRepository.save(existing);
                log.info("Updated existing Google user (partial) : {}", email);
            } else {
                log.info("Existing Google user signed in without profile changes: {}", email);
            }
            return existing;
        }

        // New user
        AppUser u = AppUser.builder()
                .email(email)
                .firstName(first)
                .lastName(last)
                .country(null)
                .provider(AuthProvider.GOOGLE)
                .role(AppRole.CUSTOMER)
                .customerNumber(numberGenerator.nextCustomerNumber())
                .profileComplete(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.save(u);
        log.info("Created new Google user: {}", email);
        return u;
    }
}
