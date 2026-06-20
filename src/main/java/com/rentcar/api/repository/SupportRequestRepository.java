package com.rentcar.api.repository;

import com.rentcar.api.domain.support.SupportRequest;
import com.rentcar.api.domain.support.SupportRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {
    @Query(
            value = """
                    select sr
                    from SupportRequest sr
                    where (:status is null or sr.status = :status)
                    order by
                      case when :status is null and sr.status = com.rentcar.api.domain.support.SupportRequestStatus.OPEN then 0 else 1 end,
                      sr.createdAt desc
                    """,
            countQuery = """
                    select count(sr)
                    from SupportRequest sr
                    where (:status is null or sr.status = :status)
                    """
    )
    Page<SupportRequest> findAdminPage(@Param("status") SupportRequestStatus status, Pageable pageable);
}
