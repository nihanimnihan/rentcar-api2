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

        return userRepository.findByEmail(email).orElseGet(() -> {
            AppUser u = AppUser.builder()
                    .email(email)
                    .firstName(first)
                    .lastName(last)
                    .country(null)
                    .provider(AuthProvider.GOOGLE)
                    .role(AppRole.CUSTOMER)
                    .customerNumber(numberGenerator.nextCustomerNumber())
                    .profileComplete((first != null && last != null))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            userRepository.save(u);
            log.info("Created new Google user: {}", email);
            return u;
        });
    }
}
