package com.rentcar.api;

import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.transfer.ChauffeurCategory;
import com.rentcar.api.dto.transfer.ChauffeurCategoryOfferResponse;
import com.rentcar.api.repository.CarRepository;
import com.rentcar.api.repository.ChauffeurCategoryRepository;
import com.rentcar.api.service.TransferOfferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TransferOfferService.
 * Mocks both repositories; no Spring context needed.
 */
@ExtendWith(MockitoExtension.class)
class TransferOfferServiceTest {

    @Mock
    private ChauffeurCategoryRepository chauffeurCategoryRepository;
    @Mock
    private CarRepository carRepository;

    private TransferOfferService transferOfferService;

    private static final LocalDateTime PICKUP = LocalDateTime.of(2099, 6, 1, 10, 0);
    private static final int DURATION = 3;
    private static final LocalDateTime DROPOFF = PICKUP.plusHours(DURATION);

    @BeforeEach
    void setUp() {
        transferOfferService = new TransferOfferService(chauffeurCategoryRepository, carRepository);
    }

    // ── Availability filtering ───────────────────────────────────────────────────

    @Test
    void getOffers_categoryWithNoAvailableCars_isExcluded() {
        ChauffeurCategory category = buildCategory(1L, "RIDE", "Ride", 3);
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category));
        when(carRepository.findAvailableChauffeurCars(eq(category), eq(PICKUP), eq(DROPOFF), isNull()))
                .thenReturn(List.of());

        List<ChauffeurCategoryOfferResponse> result = transferOfferService.getOffers(PICKUP, DURATION, null);

        assertThat(result).isEmpty();
    }

    @Test
    void getOffers_categoryWithAvailableCar_isIncluded() {
        ChauffeurCategory category = buildCategory(1L, "RIDE", "Ride", 3);
        Car car = buildCar(new BigDecimal("90.00"));
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category));
        when(carRepository.findAvailableChauffeurCars(eq(category), eq(PICKUP), eq(DROPOFF), isNull()))
                .thenReturn(List.of(car));

        List<ChauffeurCategoryOfferResponse> result = transferOfferService.getOffers(PICKUP, DURATION, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).available()).isTrue();
    }

    @Test
    void getOffers_mixedAvailability_onlyAvailableCategoriesReturned() {
        ChauffeurCategory withCar = buildCategory(1L, "RIDE", "Ride", 3);
        ChauffeurCategory empty = buildCategory(2L, "FIRST", "First", 3);
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(withCar, empty));
        when(carRepository.findAvailableChauffeurCars(eq(withCar), any(), any(), isNull()))
                .thenReturn(List.of(buildCar(new BigDecimal("90.00"))));
        when(carRepository.findAvailableChauffeurCars(eq(empty), any(), any(), isNull()))
                .thenReturn(List.of());

        List<ChauffeurCategoryOfferResponse> result = transferOfferService.getOffers(PICKUP, DURATION, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("RIDE");
    }

    // ── Passenger filter ─────────────────────────────────────────────────────────

    @Test
    void getOffers_passengersNull_doesNotFilterBySeats() {
        ChauffeurCategory small = buildCategory(1L, "RIDE", "Ride", 3);
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(small));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), isNull()))
                .thenReturn(List.of(buildCar(new BigDecimal("90.00"))));

        List<ChauffeurCategoryOfferResponse> result = transferOfferService.getOffers(PICKUP, DURATION, null);

        assertThat(result).hasSize(1);
    }

    @Test
    void getOffers_passengersFilter_excludesCategoriesWithInsufficientSeats() {
        ChauffeurCategory small = buildCategory(1L, "RIDE", "Ride", 3);
        ChauffeurCategory large = buildCategory(2L, "RIDE_XL", "Ride XL", 6);
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(small, large));
        when(carRepository.findAvailableChauffeurCars(eq(large), any(), any(), isNull()))
                .thenReturn(List.of(buildCar(new BigDecimal("140.00"))));

        List<ChauffeurCategoryOfferResponse> result = transferOfferService.getOffers(PICKUP, DURATION, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("RIDE_XL");
    }

    @Test
    void getOffers_passengersExactlyMatchSeats_categoryIsIncluded() {
        ChauffeurCategory category = buildCategory(1L, "RIDE", "Ride", 3);
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), isNull()))
                .thenReturn(List.of(buildCar(new BigDecimal("90.00"))));

        // passengers == seats (boundary: should be included)
        List<ChauffeurCategoryOfferResponse> result = transferOfferService.getOffers(PICKUP, DURATION, 3);

        assertThat(result).hasSize(1);
    }

    // ── Pricing ──────────────────────────────────────────────────────────────────

    @Test
    void getOffers_multipleCarsInCategory_selectsMinimumHourlyPrice() {
        ChauffeurCategory category = buildCategory(1L, "RIDE", "Ride", 3);
        Car expensive = buildCar(new BigDecimal("120.00"));
        Car cheap = buildCar(new BigDecimal("80.00"));
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), isNull()))
                .thenReturn(List.of(expensive, cheap));

        ChauffeurCategoryOfferResponse result = transferOfferService.getOffers(PICKUP, DURATION, null).get(0);

        assertThat(result.hourlyPriceFrom()).isEqualByComparingTo("80.00");
    }

    @Test
    void getOffers_totalPriceIsHourlyPriceFromTimesHours() {
        ChauffeurCategory category = buildCategory(1L, "RIDE", "Ride", 3);
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), isNull()))
                .thenReturn(List.of(buildCar(new BigDecimal("50.00"))));

        ChauffeurCategoryOfferResponse result = transferOfferService.getOffers(PICKUP, 4, null).get(0);

        assertThat(result.hourlyPriceFrom()).isEqualByComparingTo("50.00");
        assertThat(result.totalPrice()).isEqualByComparingTo("200.00");
    }

    // ── DTO mapping ──────────────────────────────────────────────────────────────

    @Test
    void getOffers_mapsAllCategoryFieldsToDto() {
        ChauffeurCategory category = ChauffeurCategory.builder()
                .id(42L)
                .code("GREEN")
                .name("Green")
                .description("Electric rides")
                .seats(3)
                .bags(2)
                .electric(true)
                .active(true)
                .displayOrder(2)
                .imageUrl("img/green.png")
                .build();
        when(chauffeurCategoryRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(category));
        when(carRepository.findAvailableChauffeurCars(any(), any(), any(), isNull()))
                .thenReturn(List.of(buildCar(new BigDecimal("95.00"))));

        ChauffeurCategoryOfferResponse dto = transferOfferService.getOffers(PICKUP, DURATION, null).get(0);

        assertThat(dto.categoryId()).isEqualTo(42L);
        assertThat(dto.code()).isEqualTo("GREEN");
        assertThat(dto.name()).isEqualTo("Green");
        assertThat(dto.description()).isEqualTo("Electric rides");
        assertThat(dto.seats()).isEqualTo(3);
        assertThat(dto.bags()).isEqualTo(2);
        assertThat(dto.electric()).isTrue();
        assertThat(dto.imageUrl()).isEqualTo("img/green.png");
        assertThat(dto.available()).isTrue();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private ChauffeurCategory buildCategory(Long id, String code, String name, int seats) {
        return ChauffeurCategory.builder()
                .id(id)
                .code(code)
                .name(name)
                .description("Description")
                .seats(seats)
                .bags(2)
                .electric(false)
                .active(true)
                .displayOrder(1)
                .build();
    }

    private Car buildCar(BigDecimal hourlyPrice) {
        return Car.builder()
                .hourlyPrice(hourlyPrice)
                .build();
    }
}
