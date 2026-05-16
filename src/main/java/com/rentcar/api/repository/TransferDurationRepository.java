package com.rentcar.api.repository;

import com.rentcar.api.domain.transfer.TransferDuration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransferDurationRepository extends JpaRepository<TransferDuration, Long> {

    List<TransferDuration> findByActiveTrueOrderByDisplayOrderAsc();
}
