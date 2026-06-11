package com.rentcar.api.security;

import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.repository.AppUserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final CustomOAuth2UserService oauth2UserService;
    private final AppUserRepository userRepository;

    private boolean isSafeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) return false;
        try {
            URI uri = new URI(returnTo);
            // Relative path only (no scheme, no host)
            return (uri.getScheme() == null && uri.getHost() == null && returnTo.startsWith("/"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        Map<String, Object> attrs = oauthUser.getAttributes();

        AppUser user = oauth2UserService.processGoogleUser(attrs);
        // In a simple setup, we create a Spring Security principal by setting a session attribute
        request.getSession(true).setAttribute("APP_USER_EMAIL", user.getEmail());

        // compute returnTo
        String returnTo = request.getParameter("returnTo");
        if (!isSafeReturnTo(returnTo)) {
            returnTo = request.getContextPath() + "/index.html";
        }

        // If profile incomplete redirect to signup.html with step
        if (!user.isProfileComplete()) {
            String redirect = "/signup.html?step=profile&returnTo=" + java.net.URLEncoder.encode(returnTo, java.nio.charset.StandardCharsets.UTF_8);
            response.sendRedirect(redirect);
            return;
        }

        response.sendRedirect(returnTo);
    }
}
