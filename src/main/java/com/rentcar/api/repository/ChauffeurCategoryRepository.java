package com.rentcar.api.repository;

import com.rentcar.api.domain.transfer.ChauffeurCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChauffeurCategoryRepository extends JpaRepository<ChauffeurCategory, Long> {

    List<ChauffeurCategory> findByActiveTrueOrderByDisplayOrderAsc();

    Optional<ChauffeurCategory> findByCode(String code);
}
