package com.rentcar.api.service;

import com.rentcar.api.config.MileageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculates included kilometre allowance and unlimited-km add-on price
 * for a given rental duration and car effective daily price.
 *
 * All rules come from {@link MileageProperties} — no magic numbers here.
 */
@Service
@RequiredArgsConstructor
public class MileageService {

    private final MileageProperties props;

    /**
     * Included km for the rental:
     *   days <= threshold  →  days * dailyKmBase
     *   days >  threshold  →  (threshold * dailyKmBase) + ((days - threshold) * kmPerExtraDay)
     */
    public int calculateIncludedKm(int days) {
        if (days <= props.baseDayThreshold()) {
            return days * props.dailyKmBase();
        }
        int baseKm  = props.baseDayThreshold() * props.dailyKmBase();
        int extraKm = (days - props.baseDayThreshold()) * props.kmPerExtraDay();
        return baseKm + extraKm;
    }

    /**
     * Daily price to upgrade to unlimited km:
     *   max(effectiveDailyPrice * unlimitedKmPercentage, unlimitedKmMinDailyPrice)
     */
    public BigDecimal calculateUnlimitedKmDailyPrice(BigDecimal effectiveDailyPrice) {
        BigDecimal percentageBased = effectiveDailyPrice
                .multiply(props.unlimitedKmPercentage())
                .setScale(2, RoundingMode.HALF_UP);
        return percentageBased.max(props.unlimitedKmMinDailyPrice());
    }
}
