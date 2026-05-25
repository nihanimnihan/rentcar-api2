package com.rentcar.api.repository;

import com.rentcar.api.domain.addon.Addon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AddonRepository extends JpaRepository<Addon, Long> {

    List<Addon> findByActiveTrue();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("SELECT a FROM Addon a ORDER BY a.createdAt DESC")
    List<Addon> findAllOrderByCreatedAtDesc();
}
