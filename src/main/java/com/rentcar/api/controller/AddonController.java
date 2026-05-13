package com.rentcar.api.controller;

import com.rentcar.api.dto.addon.AddonResponse;
import com.rentcar.api.dto.addon.CreateAddonRequest;
import com.rentcar.api.dto.addon.UpdateAddonRequest;
import com.rentcar.api.mapper.AddonMapper;
import com.rentcar.api.service.AddonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/addons")
@RequiredArgsConstructor
public class AddonController {

    private final AddonService addonService;
    private final AddonMapper addonMapper;

    @GetMapping("/active")
    public List<AddonResponse> getActiveAddons() {
        return addonService.getActiveAddons().stream()
                .map(addonMapper::toResponse)
                .toList();
    }

    @GetMapping
    public List<AddonResponse> getAllAddons() {
        return addonService.getAllAddons().stream()
                .map(addonMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public AddonResponse getAddonById(@PathVariable Long id) {
        return addonMapper.toResponse(addonService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddonResponse createAddon(@Valid @RequestBody CreateAddonRequest request) {
        return addonMapper.toResponse(addonService.create(request));
    }

    @PutMapping("/{id}")
    public AddonResponse updateAddon(@PathVariable Long id,
                                     @Valid @RequestBody UpdateAddonRequest request) {
        return addonMapper.toResponse(addonService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddon(@PathVariable Long id) {
        addonService.softDelete(id);
    }
}
