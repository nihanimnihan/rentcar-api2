package com.rentcar.api.repository;

import com.rentcar.api.domain.addon.BookingAddon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingAddonRepository extends JpaRepository<BookingAddon, Long> {
}
