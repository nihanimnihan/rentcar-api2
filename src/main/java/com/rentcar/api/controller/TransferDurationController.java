package com.rentcar.api.controller;

import com.rentcar.api.dto.transfer.TransferDurationResponse;
import com.rentcar.api.service.TransferDurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferDurationController {

    private final TransferDurationService transferDurationService;

    @GetMapping("/durations")
    public List<TransferDurationResponse> getDurations() {
        return transferDurationService.getActiveDurations();
    }
}
