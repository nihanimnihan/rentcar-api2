package com.rentcar.api.service;

import com.rentcar.api.domain.insurance.InsuranceCoverageItem;
import com.rentcar.api.domain.insurance.InsurancePackage;
import com.rentcar.api.dto.insurance.InsuranceCoverageItemResponse;
import com.rentcar.api.dto.insurance.InsurancePackageResponse;
import com.rentcar.api.repository.InsurancePackageRepository;
import com.rentcar.api.util.LanguageNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsurancePackageService {

    private final InsurancePackageRepository insurancePackageRepository;

    public List<InsurancePackageResponse> getActivePackages(String language) {
        String lang = LanguageNormalizer.normalizeOrDefault(language);
        return insurancePackageRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(pkg -> toResponse(pkg, lang))
                .toList();
    }

    public InsurancePackage getActivePackage(Long id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insurance package is required.");
        }
        return insurancePackageRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected insurance package is not available."));
    }

    public String localizedName(InsurancePackage pkg, String language) {
        return localized(language, pkg.getNameEn(), pkg.getNameEs(), pkg.getNameTr());
    }

    private InsurancePackageResponse toResponse(InsurancePackage pkg, String language) {
        return new InsurancePackageResponse(
                pkg.getId(),
                pkg.getCode(),
                localized(language, pkg.getNameEn(), pkg.getNameEs(), pkg.getNameTr()),
                localized(language, pkg.getDescriptionEn(), pkg.getDescriptionEs(), pkg.getDescriptionTr()),
                pkg.getPricePerDay(),
                pkg.getDepositAmount(),
                pkg.getDisplayOrder(),
                pkg.isRecommended(),
                localized(language, pkg.getBadgeEn(), pkg.getBadgeEs(), pkg.getBadgeTr()),
                pkg.getCoverageItems().stream()
                        .sorted(Comparator.comparingInt(InsuranceCoverageItem::getDisplayOrder))
                        .map(item -> new InsuranceCoverageItemResponse(
                                item.getId(),
                                localized(language, item.getTitleEn(), item.getTitleEs(), item.getTitleTr()),
                                localized(language, item.getDescriptionEn(), item.getDescriptionEs(), item.getDescriptionTr()),
                                item.isIncluded(),
                                item.getDisplayOrder()))
                        .toList()
        );
    }

    private String localized(String language, String en, String es, String tr) {
        String normalized = LanguageNormalizer.normalizeOrDefault(language);
        return switch (normalized) {
            case "es" -> blankToFallback(es, en);
            case "tr" -> blankToFallback(tr, en);
            default -> en;
        };
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
