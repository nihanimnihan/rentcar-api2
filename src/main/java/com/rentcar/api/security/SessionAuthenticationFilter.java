package com.rentcar.api.security;

import com.rentcar.api.domain.user.AppUser;
import com.rentcar.api.repository.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticationFilter.class);

    private final AppUserRepository userRepository;

    public SessionAuthenticationFilter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Object emailObj = request.getSession(false) != null ? request.getSession(false).getAttribute("APP_USER_EMAIL") : null;
                if (emailObj instanceof String) {
                    String email = ((String) emailObj).toLowerCase();
                    Optional<AppUser> maybe = userRepository.findByEmail(email);
                    if (maybe.isPresent()) {
                        AppUser u = maybe.get();
                        List<SimpleGrantedAuthority> auths = u.getRole() == null ? List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")) :
                                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
                        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(u.getEmail(), null, auths);
                        SecurityContextHolder.getContext().setAuthentication(token);
                        // do not modify session here
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("SessionAuthenticationFilter error: {}", ex.toString());
        }
        filterChain.doFilter(request, response);
    }
}
