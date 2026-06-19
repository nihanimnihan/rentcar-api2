package com.rentcar.api.service;

import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.exception.CustomerNotFoundException;
import com.rentcar.api.repository.CustomerRepository;
import com.rentcar.api.util.LanguageNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Finds an existing customer by email or creates one — atomically.
     *
     * Race-condition handling:
     *   Thread A and Thread B both call this concurrently for the same email.
     *   Both SELECT and find nothing. Both attempt INSERT.
     *   The UNIQUE constraint on `email` lets exactly one INSERT succeed.
     *   The loser catches DataIntegrityViolationException and re-SELECTs.
     *
     * REQUIRES_NEW is mandatory: this method needs its own DB transaction so that
     * a caught DataIntegrityViolationException does not mark the outer booking
     * transaction as rollback-only. saveAndFlush() is mandatory: it forces the
     * INSERT to hit the DB immediately so the exception is thrown here, not at
     * the outer transaction commit boundary where we can no longer catch it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer getOrCreateCustomer(String name, String email, String phone) {
        return getOrCreateCustomer(name, email, phone, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer getOrCreateCustomer(String name, String email, String phone, String language) {
        String preferredLanguage = LanguageNormalizer.normalizeOrDefault(language);
        return customerRepository.findByEmail(email)
                .map(c -> {
                    if (c.getPreferredLanguage() == null || c.getPreferredLanguage().isBlank()) {
                        c.setPreferredLanguage(preferredLanguage);
                    }
                    log.debug("Existing customer matched: id={}", c.getId());
                    return c;
                })
                .orElseGet(() -> {
                    try {
                        Customer created = customerRepository.saveAndFlush(
                                Customer.builder()
                                        .fullName(name)
                                        .email(email)
                                        .phone(phone)
                                        .preferredLanguage(preferredLanguage)
                                        .build()
                        );
                        log.info("New customer created: id={}", created.getId());
                        return created;
                    } catch (DataIntegrityViolationException e) {
                        // Another thread inserted the same email between our SELECT and INSERT.
                        // The UNIQUE constraint caught it — re-select to get the existing row.
                        log.warn("Concurrent customer insert detected — re-selecting existing record");
                        Customer existing = customerRepository.findByEmail(email)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Customer not found after duplicate key for email: " + email, e));
                        if (existing.getPreferredLanguage() == null || existing.getPreferredLanguage().isBlank()) {
                            existing.setPreferredLanguage(preferredLanguage);
                        }
                        return existing;
                    }
                });
    }

    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
