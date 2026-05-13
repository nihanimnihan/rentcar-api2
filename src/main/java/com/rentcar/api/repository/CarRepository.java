package com.rentcar.api.repository;

import com.rentcar.api.domain.car.Car;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarRepository extends JpaRepository<Car, Long> {
    List<Car> findByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Car c WHERE c.id = :id")
    Optional<Car> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        select c
        from Car c
        left join Booking b on b.car = c and b.status = com.rentcar.api.domain.booking.BookingStatus.CONFIRMED
        group by c
        order by count(b.id) desc
    """)
    List<Car> findPopularCars();
}