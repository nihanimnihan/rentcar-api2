package com.rentcar.api.service;

import com.rentcar.api.dto.transfer.TransferDurationResponse;
import com.rentcar.api.repository.TransferDurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferDurationService {

    private final TransferDurationRepository transferDurationRepository;

    public List<TransferDurationResponse> getActiveDurations() {
        return transferDurationRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(d -> new TransferDurationResponse(
                        d.getId(),
                        d.getHours(),
                        d.getIncludedKm()
                ))
                .toList();
    }
}
