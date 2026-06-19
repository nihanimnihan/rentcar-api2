package com.rentcar.api.security;

import com.rentcar.api.domain.user.AppRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleAssignmentService {

    @Value("${rentcar.super-admin.emails:}")
    private String superAdminEmails;

    @Value("${rentcar.admin.emails:}")
    private String adminEmails;

    @Value("${rentcar.staff.emails:}")
    private String staffEmails;

    private Set<String> superAdmins = Collections.emptySet();
    private Set<String> admins = Collections.emptySet();
    private Set<String> staffs = Collections.emptySet();

    @PostConstruct
    public void init() {
        superAdmins = parseList(superAdminEmails);
        admins = parseList(adminEmails);
        staffs = parseList(staffEmails);
    }

    private Set<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptySet();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(HashSet::new));
    }

    public AppRole resolveRoleForEmail(String email) {
        if (email == null) return AppRole.CUSTOMER;
        String e = email.trim().toLowerCase();
        if (superAdmins.contains(e)) return AppRole.SUPER_ADMIN;
        if (admins.contains(e)) return AppRole.ADMIN;
        if (staffs.contains(e)) return AppRole.STAFF;
        return AppRole.CUSTOMER;
    }
}
