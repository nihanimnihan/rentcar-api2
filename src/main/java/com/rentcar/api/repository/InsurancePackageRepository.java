package com.rentcar.api.repository;

import com.rentcar.api.domain.insurance.InsurancePackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InsurancePackageRepository extends JpaRepository<InsurancePackage, Long> {
    List<InsurancePackage> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<InsurancePackage> findByIdAndActiveTrue(Long id);

    Optional<InsurancePackage> findByCode(String code);
}
