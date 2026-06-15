package com.rentcar.api.service;

import com.rentcar.api.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class CustomerNumberGenerator {

    private final AppUserRepository repo;
    private final SecureRandom rnd = new SecureRandom();
    private final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public synchronized String nextCustomerNumber() {
        String prefix = "RC";
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));

        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) sb.append(ALPHANUM.charAt(rnd.nextInt(ALPHANUM.length())));
            String candidate = prefix + date + sb.toString();
            if (!repo.existsByCustomerNumber(candidate)) return candidate;
        }
        // Fallback (extremely unlikely): use a random UUID fragment
        String uuidFrag = Long.toHexString(Math.abs(rnd.nextLong())).toUpperCase();
        return prefix + date + uuidFrag.substring(0, Math.min(4, uuidFrag.length()));
    }
}
