package com.rentcar.api.controller;

import com.rentcar.api.dto.transfer.ChauffeurCategoryOfferResponse;
import com.rentcar.api.dto.transfer.CreateTransferBookingRequest;
import com.rentcar.api.dto.transfer.TransferBookingResponse;
import com.rentcar.api.dto.transfer.TransferDurationResponse;
import com.rentcar.api.service.TransferBookingService;
import com.rentcar.api.service.TransferDurationService;
import com.rentcar.api.service.TransferOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferDurationController {

    private static final DateTimeFormatter PICKUP_DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final TransferDurationService transferDurationService;
    private final TransferOfferService transferOfferService;
    private final TransferBookingService transferBookingService;

    @GetMapping("/durations")
    public List<TransferDurationResponse> getDurations() {
        return transferDurationService.getActiveDurations();
    }

    @GetMapping("/offers")
    public List<ChauffeurCategoryOfferResponse> getOffers(
            @RequestParam String pickupDateTime,
            @RequestParam int durationHours,
            @RequestParam(required = false) String pickupLocation,
            @RequestParam(required = false) Integer passengers) {

        LocalDateTime pickup = LocalDateTime.parse(
                pickupDateTime.trim().replace(' ', 'T'), PICKUP_DT_FMT);

        return transferOfferService.getOffers(pickup, durationHours, passengers);
    }

    @PostMapping("/bookings")
    public TransferBookingResponse createTransferBooking(
            @Valid @RequestBody CreateTransferBookingRequest request) {
        return transferBookingService.createTransferBooking(request);
    }
}
