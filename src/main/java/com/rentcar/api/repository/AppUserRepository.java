package com.rentcar.api.repository;

import com.rentcar.api.domain.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    boolean existsByCustomerNumber(String customerNumber);

    @Query("SELECT COALESCE(MAX(u.id), 0) FROM AppUser u")
    Long maxId();
}
