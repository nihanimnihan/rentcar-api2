package com.rentcar.api;

import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.domain.transfer.ChauffeurCategory;
import com.rentcar.api.dto.transfer.CreateTransferBookingRequest;
import com.rentcar.api.dto.transfer.TransferBookingResponse;
import com.rentcar.api.exception.ChauffeurCategoryNotFoundException;
import com.rentcar.api.exception.InvalidTransferRequestException;
import com.rentcar.api.exception.NoChauffeurCarAvailableException;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.CarRepository;
import com.rentcar.api.repository.ChauffeurCategoryRepository;
import com.rentcar.api.service.CarService;
import com.rentcar.api.service.CustomerService;
import com.rentcar.api.service.TransferBookingService;
import com.rentcar.api.util.BookingReferenceGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TransferBookingService.
 * Spring context is not loaded — all dependencies are mocked.
 */
@ExtendWith(MockitoExtension.class)
class TransferBookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private CarRepository carRepository;
    @Mock private ChauffeurCategoryRepository chauffeurCategoryRepository;
    @Mock private CarService carService;
    @Mock private CustomerService customerService;
    @Mock private BookingReferenceGenerator referenceGenerator;
    @Mock private com.rentcar.api.util.AppClock appClock;

    @InjectMocks
    private TransferBookingService transferBookingService;

    @BeforeEach
    void commonSetup() {
        org.mockito.Mockito.lenient().when(appClock.nowUtc()).thenReturn(Instant.now());
    }

    private static final LocalDateTime PICKUP = LocalDateTime.now().plusDays(10);

    // ── 1. Happy path ─────────────────────────────────────────────────────────

    @Test
    void createTransferBooking_validRequest_returnsConfirmationResponse() {
        ChauffeurCategory cat = category(1L, "RIDE", "Ride", 3);
        Car car = car(10L, cat, new BigDecimal("95.00"));
        Customer customer = customer(20L, "Alice", "alice@example.com");

        when(chauffeurCategoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), any(), any())).thenReturn(List.of(car));
        when(carService.getActiveCarByIdForUpdate(10L)).thenReturn(car);
        when(bookingRepository.existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                any(), any(), any(), any())).thenReturn(false);
        when(customerService.getOrCreateCustomer(any(), any(), any())).thenReturn(customer);
        when(referenceGenerator.generate()).thenReturn("RC-260521-TEST");
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            com.rentcar.api.domain.booking.Booking b = inv.getArgument(0);
            b.setId(99L);
            return b;
        });

        TransferBookingResponse response = transferBookingService.createTransferBooking(
                request(1L, PICKUP, 3, 2, null));

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.customerName()).isEqualTo("Alice");
        assertThat(response.customerEmail()).isEqualTo("alice@example.com");
        assertThat(response.categoryCode()).isEqualTo("RIDE");
        assertThat(response.durationHours()).isEqualTo(3);
        assertThat(response.passengers()).isEqualTo(2);

        ArgumentCaptor<com.rentcar.api.domain.booking.Booking> bookingCaptor =
                ArgumentCaptor.forClass(com.rentcar.api.domain.booking.Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        var savedBooking = bookingCaptor.getValue();
        assertThat(savedBooking.getRentalDetails()).isNull();
        assertThat(savedBooking.getTransferDetails()).isNotNull();
        assertThat(savedBooking.getTransferDetails().getDurationHours()).isEqualTo(3);
        assertThat(savedBooking.getTransferDetails().getPassengers()).isEqualTo(2);
        assertThat(savedBooking.getTransferDetails().getHourlyPriceSnapshot()).isEqualByComparingTo("95.00");
        assertThat(savedBooking.getTransferDetails().getChauffeurCategoryCode()).isEqualTo("RIDE");
    }

    // ── 2. Category not found ─────────────────────────────────────────────────

    @Test
    void createTransferBooking_categoryNotFound_throws404Exception() {
        when(chauffeurCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transferBookingService.createTransferBooking(request(999L, PICKUP, 2, null, null)))
                .isInstanceOf(ChauffeurCategoryNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── 3. No available cars ──────────────────────────────────────────────────

    @Test
    void createTransferBooking_noAvailableCar_throwsNoChauffeurCarAvailableException() {
        ChauffeurCategory cat = category(1L, "RIDE", "Ride", 3);

        when(chauffeurCategoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() ->
                transferBookingService.createTransferBooking(request(1L, PICKUP, 2, null, null)))
                .isInstanceOf(NoChauffeurCarAvailableException.class);
    }

    // ── 4. Passengers exceed seat capacity ────────────────────────────────────

    @Test
    void createTransferBooking_passengersExceedSeats_throwsInvalidTransferRequestException() {
        ChauffeurCategory cat = category(1L, "RIDE", "Ride", 3);

        when(chauffeurCategoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        assertThatThrownBy(() ->
                transferBookingService.createTransferBooking(request(1L, PICKUP, 2, 10, null)))
                .isInstanceOf(InvalidTransferRequestException.class)
                .hasMessageContaining("10")
                .hasMessageContaining("3");
    }

    // ── 5. Assigned car belongs to requested category ─────────────────────────

    @Test
    void createTransferBooking_assignedCarBelongsToRequestedCategory() {
        ChauffeurCategory cat = category(1L, "RIDE", "Ride", 3);
        Car car = car(10L, cat, new BigDecimal("95.00"));
        Customer customer = customer(20L, "Bob", "bob@example.com");

        when(chauffeurCategoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), any(), any())).thenReturn(List.of(car));
        when(carService.getActiveCarByIdForUpdate(10L)).thenReturn(car);
        when(bookingRepository.existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                any(), any(), any(), any())).thenReturn(false);
        when(customerService.getOrCreateCustomer(any(), any(), any())).thenReturn(customer);
        when(referenceGenerator.generate()).thenReturn("RC-260521-TEST");
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            com.rentcar.api.domain.booking.Booking b = inv.getArgument(0);
            b.setId(88L);
            return b;
        });

        TransferBookingResponse response = transferBookingService.createTransferBooking(
                request(1L, PICKUP, 2, null, null));

        // The response returns category info from the category entity, not from the car field,
        // but both must agree — verifies the car was selected for the requested category.
        assertThat(response.categoryCode()).isEqualTo(car.getChauffeurCategory().getCode());
    }

    // ── 6. Pricing: totalPrice = hourlyPrice * durationHours ─────────────────

    @Test
    void createTransferBooking_totalPrice_isHourlyPriceTimesHours() {
        ChauffeurCategory cat = category(1L, "FIRST", "First", 3);
        Car car = car(10L, cat, new BigDecimal("250.00"));
        Customer customer = customer(20L, "Carol", "carol@example.com");

        when(chauffeurCategoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), any(), any())).thenReturn(List.of(car));
        when(carService.getActiveCarByIdForUpdate(10L)).thenReturn(car);
        when(bookingRepository.existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                any(), any(), any(), any())).thenReturn(false);
        when(customerService.getOrCreateCustomer(any(), any(), any())).thenReturn(customer);
        when(referenceGenerator.generate()).thenReturn("RC-260521-TEST");
        when(bookingRepository.save(any())).thenAnswer(inv -> {
            com.rentcar.api.domain.booking.Booking b = inv.getArgument(0);
            b.setId(77L);
            return b;
        });

        TransferBookingResponse response = transferBookingService.createTransferBooking(
                request(1L, PICKUP, 4, null, null)); // 4 hours

        // 250.00 * 4 = 1000.00
        assertThat(response.totalPrice()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.hourlyPrice()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    // ── 7. Overlap race-condition: re-check after lock returns conflict ────────

    @Test
    void createTransferBooking_overlapFoundAfterLock_throwsNoChauffeurCarAvailableException() {
        ChauffeurCategory cat = category(1L, "RIDE", "Ride", 3);
        Car car = car(10L, cat, new BigDecimal("95.00"));

        when(chauffeurCategoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), any(), any())).thenReturn(List.of(car));
        when(carService.getActiveCarByIdForUpdate(10L)).thenReturn(car);
        // Overlap detected after acquiring the lock (race condition)
        when(bookingRepository.existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() ->
                transferBookingService.createTransferBooking(request(1L, PICKUP, 2, null, null)))
                .isInstanceOf(NoChauffeurCarAvailableException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ChauffeurCategory category(Long id, String code, String name, int seats) {
        return ChauffeurCategory.builder()
                .id(id).code(code).name(name)
                .seats(seats).bags(2).electric(false)
                .active(true).displayOrder(1)
                .build();
    }

    private Car car(Long id, ChauffeurCategory category, BigDecimal hourlyPrice) {
        return Car.builder()
                .id(id).brand("BMW").model("X1")
                .seats(4).bags(2).active(true)
                .chauffeurAvailable(true)
                .chauffeurCategory(category)
                .hourlyPrice(hourlyPrice)
                .dailyPrice(new BigDecimal("100.00"))
                .airConditioning(true).premium(false).guaranteedModel(false)
                .doors(4).minDriverAge(21).displayClass("SUV")
                .build();
    }

    private Customer customer(Long id, String name, String email) {
        return Customer.builder().id(id).fullName(name).email(email).phone("+34600000001").build();
    }

    private CreateTransferBookingRequest request(
            Long categoryId, LocalDateTime pickup, int durationHours,
            Integer passengers, String notes) {
        return new CreateTransferBookingRequest(
                "Test User", "test@example.com", "+34600000099",
                pickup, durationHours, categoryId, passengers, notes);
    }
}
