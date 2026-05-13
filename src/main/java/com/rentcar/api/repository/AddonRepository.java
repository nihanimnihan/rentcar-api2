package com.rentcar.api.repository;

import com.rentcar.api.domain.addon.Addon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AddonRepository extends JpaRepository<Addon, Long> {

    List<Addon> findByActiveTrue();
}
