package com.rentcar.api;

import com.rentcar.api.config.MileageProperties;
import com.rentcar.api.service.MileageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MileageService — no Spring context needed.
 *
 * Configuration under test:
 *   dailyKmBase = 300, kmPerExtraDay = 150, baseDayThreshold = 7
 *   unlimitedKmPercentage = 0.10, unlimitedKmMinDailyPrice = 4.70
 */
class MileageServiceTest {

    private MileageService mileageService;

    @BeforeEach
    void setUp() {
        MileageProperties props = new MileageProperties(
                300,                        // dailyKmBase
                150,                        // kmPerExtraDay
                7,                          // baseDayThreshold
                new BigDecimal("0.10"),     // unlimitedKmPercentage
                new BigDecimal("4.70")      // unlimitedKmMinDailyPrice
        );
        mileageService = new MileageService(props);
    }

    // ── Included km — boundary values ───────────────────────────────────────────

    @Test
    void oneDay_300km() {
        assertThat(mileageService.calculateIncludedKm(1)).isEqualTo(300);
    }

    @Test
    void sevenDays_2100km() {
        assertThat(mileageService.calculateIncludedKm(7)).isEqualTo(2100);
    }

    @Test
    void tenDays_2550km() {
        // 7*300 + 3*150 = 2100 + 450
        assertThat(mileageService.calculateIncludedKm(10)).isEqualTo(2550);
    }

    @Test
    void thirteenDays_3000km() {
        // 7*300 + 6*150 = 2100 + 900
        assertThat(mileageService.calculateIncludedKm(13)).isEqualTo(3000);
    }

    @ParameterizedTest(name = "{0} days → {1} km")
    @CsvSource({
            "1,   300",
            "2,   600",
            "3,   900",
            "6,  1800",
            "7,  2100",
            "8,  2250",
            "14, 3150",
            "28, 5250"
    })
    void includedKm_parameterized(int days, int expectedKm) {
        assertThat(mileageService.calculateIncludedKm(days)).isEqualTo(expectedKm);
    }

    // ── Unlimited km daily price ─────────────────────────────────────────────────

    @Test
    void unlimitedKm_percentageApplied_whenAboveFloor() {
        // effectiveDaily = 95.00 → 10% = 9.50 > 4.70 → use 9.50
        BigDecimal price = mileageService.calculateUnlimitedKmDailyPrice(new BigDecimal("95.00"));
        assertThat(price).isEqualByComparingTo("9.50");
    }

    @Test
    void unlimitedKm_floorApplied_whenPercentageBelowMinimum() {
        // effectiveDaily = 30.00 → 10% = 3.00 < 4.70 → use floor 4.70
        BigDecimal price = mileageService.calculateUnlimitedKmDailyPrice(new BigDecimal("30.00"));
        assertThat(price).isEqualByComparingTo("4.70");
    }

    @Test
    void unlimitedKm_exactFloor_whenPercentageEqualsMinimum() {
        // effectiveDaily = 47.00 → 10% = 4.70 == floor → use 4.70
        BigDecimal price = mileageService.calculateUnlimitedKmDailyPrice(new BigDecimal("47.00"));
        assertThat(price).isEqualByComparingTo("4.70");
    }

    @Test
    void unlimitedKm_roundedToTwoDecimals() {
        // effectiveDaily = 100.00 → 10% = 10.00 — exactly 2dp
        BigDecimal price = mileageService.calculateUnlimitedKmDailyPrice(new BigDecimal("100.00"));
        assertThat(price.scale()).isEqualTo(2);
    }

    @Test
    void unlimitedKm_largePrice_noFloor() {
        // effectiveDaily = 200.00 → 10% = 20.00 > 4.70
        BigDecimal price = mileageService.calculateUnlimitedKmDailyPrice(new BigDecimal("200.00"));
        assertThat(price).isEqualByComparingTo("20.00");
    }
}
