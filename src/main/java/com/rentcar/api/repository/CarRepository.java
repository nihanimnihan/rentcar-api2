package com.rentcar.api.repository;

import com.rentcar.api.domain.car.Car;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarRepository extends JpaRepository<Car, Long> {
    List<Car> findByActiveTrue();
    @Query("""
        select c
        from Car c
        left join Booking b on b.car = c and b.status = com.rentcar.api.domain.booking.BookingStatus.CONFIRMED
        group by c
        order by count(b.id) desc
    """)
    List<Car> findPopularCars();
}