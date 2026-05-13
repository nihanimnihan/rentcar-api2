package com.rentcar.api.service;

import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.dto.addon.CreateAddonRequest;
import com.rentcar.api.dto.addon.UpdateAddonRequest;
import com.rentcar.api.exception.AddonNotFoundException;
import com.rentcar.api.repository.AddonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddonService {

    private final AddonRepository addonRepository;

    public List<Addon> getActiveAddons() {
        return addonRepository.findByActiveTrue();
    }

    public List<Addon> getAllAddons() {
        return addonRepository.findAll();
    }

    public Addon getById(Long id) {
        return addonRepository.findById(id)
                .orElseThrow(() -> new AddonNotFoundException(id));
    }

    @Transactional
    public Addon create(CreateAddonRequest request) {
        Addon addon = Addon.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .pricingType(request.pricingType())
                .active(request.active())
                .build();
        return addonRepository.save(addon);
    }

    @Transactional
    public Addon update(Long id, UpdateAddonRequest request) {
        Addon addon = getById(id);
        addon.setName(request.name());
        addon.setDescription(request.description());
        addon.setPrice(request.price());
        addon.setPricingType(request.pricingType());
        addon.setActive(request.active());
        return addonRepository.save(addon);
    }

    @Transactional
    public void softDelete(Long id) {
        Addon addon = getById(id);
        addon.setActive(false);
        addonRepository.save(addon);
    }
}
