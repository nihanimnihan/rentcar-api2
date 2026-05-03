package com.rentcar.api.service;

import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.exception.CustomerNotFoundException;
import com.rentcar.api.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {
    private final CustomerRepository customerRepository;

    public Customer getOrCreateCustomer(String name, String email, String phone) {

        return customerRepository.findByEmailAndPhone(email, phone)
                .orElseGet(() -> customerRepository.save(
                        Customer.builder()
                                .fullName(name)
                                .email(email)
                                .phone(phone)
                                .build()
                ));
    }

    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
