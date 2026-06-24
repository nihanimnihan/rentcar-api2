package com.rentcar.api.repository;

import com.rentcar.api.domain.handover.BookingDeposit;
import com.rentcar.api.domain.handover.DepositRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepositRefundRepository extends JpaRepository<DepositRefund, Long> {

    List<DepositRefund> findAllByDepositOrderByCreatedAtDescIdDesc(BookingDeposit deposit);
}
