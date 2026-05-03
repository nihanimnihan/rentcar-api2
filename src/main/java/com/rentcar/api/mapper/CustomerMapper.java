package com.rentcar.api.mapper;

import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.dto.customer.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {
    CustomerResponse toResponse(Customer customer);
}
