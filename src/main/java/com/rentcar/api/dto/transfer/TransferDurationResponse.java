package com.rentcar.api.dto.transfer;

public record TransferDurationResponse(
        Long id,
        int hours,
        int includedKm
) {
}
