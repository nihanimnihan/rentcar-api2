package com.rentcar.api.service;

import com.rentcar.api.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerNumberGenerator {

    private final AppUserRepository repo;

    public synchronized String nextCustomerNumber() {
        Long maxId = repo.maxId();
        long next = (maxId == null ? 1L : maxId + 1L) + 1000000L; // start at 1000001
        return String.format("RC-CUST-%d", next);
    }
}
