package com.rentcar.api.service;

import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.dto.admin.AdminAddonRequest;
import com.rentcar.api.dto.admin.AdminAddonResponse;
import com.rentcar.api.exception.AddonNotFoundException;
import com.rentcar.api.exception.DuplicateAddonCodeException;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.BookingAddonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAddonService {

    private final AddonRepository addonRepository;
    private final BookingAddonRepository bookingAddonRepository;

    public List<AdminAddonResponse> list() {
        return addonRepository.findAllOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public AdminAddonResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public AdminAddonResponse create(AdminAddonRequest request) {
        if (request.code() != null && !request.code().isBlank()
                && addonRepository.existsByCode(request.code())) {
            throw new DuplicateAddonCodeException(request.code());
        }
        Addon addon = Addon.builder()
                .name(request.name())
                .nameEs(request.nameEs())
                .code(request.code())
                .description(request.description())
                .descriptionEs(request.descriptionEs())
                .price(request.price())
                .pricingType(request.pricingType())
                .imageUrl(request.imageUrl())
                .recommended(request.recommended())
                .active(request.active())
                .build();
        return toResponse(addonRepository.save(addon));
    }

    @Transactional
    public AdminAddonResponse update(Long id, AdminAddonRequest request) {
        Addon addon = findOrThrow(id);

        if (request.code() != null && !request.code().isBlank()
                && addonRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw new DuplicateAddonCodeException(request.code());
        }

        addon.setName(request.name());
        addon.setNameEs(request.nameEs());
        addon.setCode(request.code());
        addon.setDescription(request.description());
        addon.setDescriptionEs(request.descriptionEs());
        addon.setPrice(request.price());
        addon.setPricingType(request.pricingType());
        addon.setImageUrl(request.imageUrl());
        addon.setRecommended(request.recommended());
        addon.setActive(request.active());

        return toResponse(addonRepository.save(addon));
    }

    @Transactional
    public AdminAddonResponse setActive(Long id, boolean active) {
        Addon addon = findOrThrow(id);
        addon.setActive(active);
        return toResponse(addonRepository.save(addon));
    }

    /**
     * Always soft-deletes (sets active=false) to preserve historical BookingAddon snapshots.
     * If the addon has no historical records, it is still soft-deleted for safety.
     * Hard deletion is intentionally not supported via this API.
     */
    @Transactional
    public void delete(Long id) {
        Addon addon = findOrThrow(id);
        addon.setActive(false);
        addonRepository.save(addon);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private Addon findOrThrow(Long id) {
        return addonRepository.findById(id)
                .orElseThrow(() -> new AddonNotFoundException(id));
    }

    private AdminAddonResponse toResponse(Addon a) {
        return new AdminAddonResponse(
                a.getId(),
                a.getName(),
                a.getNameEs(),
                a.getCode(),
                a.getDescription(),
                a.getDescriptionEs(),
                a.getPrice(),
                a.getPricingType().name(),
                a.getImageUrl(),
                a.isRecommended(),
                a.isActive(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
