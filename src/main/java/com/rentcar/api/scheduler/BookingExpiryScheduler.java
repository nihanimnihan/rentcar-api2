package com.rentcar.api.scheduler;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final AppClock appClock;

    @Scheduled(fixedDelayString = "PT1M") // every 1 minute
    @Transactional
    public void expirePendingBookings() {
        Instant now = appClock.nowUtc();
        List<Booking> expired = bookingRepository.findPendingBookingsExpired(now);
        if (expired.isEmpty()) return;
        for (Booking b : expired) {
            try {
                b.setStatus(BookingStatus.EXPIRED);
                b.setExpiresAt(null);
                bookingRepository.save(b);
                log.info("Expired booking id={} reference={}", b.getId(), b.getBookingReference());
            } catch (Exception e) {
                log.warn("Failed to expire booking id={}: {}", b.getId(), e.getMessage());
            }
        }
    }
}
