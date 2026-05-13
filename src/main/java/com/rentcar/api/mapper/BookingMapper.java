package com.rentcar.api.mapper;

import com.rentcar.api.domain.addon.BookingAddon;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.addon.BookingAddonResponse;
import com.rentcar.api.dto.booking.BookingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "dailyPrice", source = "discountedDailyPrice")
    @Mapping(target = "addons", source = "bookingAddons")
    BookingResponse toResponse(Booking booking);

    @Mapping(target = "addonId", source = "addon.id")
    @Mapping(target = "name", source = "addonName")
    BookingAddonResponse toAddonResponse(BookingAddon bookingAddon);
}