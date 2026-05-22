package com.rentcar.api.mapper;

import com.rentcar.api.domain.addon.BookingAddon;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.addon.BookingAddonResponse;
import com.rentcar.api.dto.booking.BookingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "effectiveDailyPrice", source = "discountedDailyPrice")
    @Mapping(target = "carRentalTotal", source = "rentalCharge")
    @Mapping(target = "addonTotal", source = "addonCharge")
    @Mapping(target = "addons", source = "bookingAddons")
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "paymentMethod", ignore = true)
    BookingResponse toResponse(Booking booking);

    @Mapping(target = "addonId", source = "addon.id")
    @Mapping(target = "name", source = "addonName")
    @Mapping(target = "pricingTypeSnapshot", expression = "java(bookingAddon.getPricingTypeSnapshot().name())")
    @Mapping(target = "lineTotal", source = "priceAtBooking")
    BookingAddonResponse toAddonResponse(BookingAddon bookingAddon);
}