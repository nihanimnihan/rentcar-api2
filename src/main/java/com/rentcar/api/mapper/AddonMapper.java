package com.rentcar.api.mapper;

import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.dto.addon.AddonResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AddonMapper {

    AddonResponse toResponse(Addon addon);
}
