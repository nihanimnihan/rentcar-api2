package com.rentcar.api.controller;

import com.rentcar.api.domain.transfer.ChauffeurCategory;
import com.rentcar.api.dto.admin.AdminCarRequest;
import com.rentcar.api.dto.admin.AdminCarResponse;
import com.rentcar.api.service.AdminCarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

// TODO before production: protect with hasRole("ADMIN") in SecurityConfig
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCarController {

    private final AdminCarService adminCarService;

    // ── Cars ──────────────────────────────────────────────────────────────────

    @GetMapping("/cars")
    public List<AdminCarResponse> list() {
        return adminCarService.list();
    }

    @GetMapping("/cars/{id}")
    public AdminCarResponse getById(@PathVariable Long id) {
        return adminCarService.getById(id);
    }

    @PostMapping("/cars")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCarResponse create(@Valid @RequestBody AdminCarRequest request) {
        return adminCarService.create(request);
    }

    @PutMapping("/cars/{id}")
    public AdminCarResponse update(@PathVariable Long id,
                                   @Valid @RequestBody AdminCarRequest request) {
        return adminCarService.update(id, request);
    }

    @PatchMapping("/cars/{id}/active")
    public AdminCarResponse setActive(@PathVariable Long id,
                                      @RequestParam boolean value) {
        return adminCarService.setActive(id, value);
    }

    @DeleteMapping("/cars/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        adminCarService.delete(id);
    }

    // ── Chauffeur categories (for car form dropdown) ──────────────────────────

    @GetMapping("/chauffeur-categories")
    public List<Map<String, Object>> listChauffeurCategories() {
        return adminCarService.listChauffeurCategories().stream()
                .map(c -> Map.<String, Object>of(
                        "id", c.getId(),
                        "code", c.getCode(),
                        "name", c.getName(),
                        "seats", c.getSeats()))
                .toList();
    }
}
