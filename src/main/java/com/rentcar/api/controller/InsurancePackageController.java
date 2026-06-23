package com.rentcar.api.controller;

import com.rentcar.api.dto.insurance.InsurancePackageResponse;
import com.rentcar.api.service.InsurancePackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/insurance-packages")
@RequiredArgsConstructor
public class InsurancePackageController {

    private final InsurancePackageService insurancePackageService;

    @GetMapping("/active")
    public List<InsurancePackageResponse> activePackages(
            @RequestParam(value = "lang", required = false) String lang,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        return insurancePackageService.getActivePackages(lang != null ? lang : acceptLanguage);
    }
}
