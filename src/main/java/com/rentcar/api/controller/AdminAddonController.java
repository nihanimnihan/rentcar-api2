package com.rentcar.api.controller;

import com.rentcar.api.dto.admin.AdminAddonRequest;
import com.rentcar.api.dto.admin.AdminAddonResponse;
import com.rentcar.api.service.AdminAddonService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// TODO before production: protect with hasRole("ADMIN") in SecurityConfig
@RestController
@RequestMapping("/api/admin/addons")
@RequiredArgsConstructor
public class AdminAddonController {

    private final AdminAddonService adminAddonService;

    @GetMapping
    public List<AdminAddonResponse> list() {
        return adminAddonService.list();
    }

    @GetMapping("/{id}")
    public AdminAddonResponse getById(@PathVariable Long id) {
        return adminAddonService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminAddonResponse create(@Valid @RequestBody AdminAddonRequest request) {
        return adminAddonService.create(request);
    }

    @PutMapping("/{id}")
    public AdminAddonResponse update(@PathVariable Long id,
                                     @Valid @RequestBody AdminAddonRequest request) {
        return adminAddonService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminAddonResponse setActive(@PathVariable Long id,
                                        @RequestParam boolean value) {
        return adminAddonService.setActive(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        adminAddonService.delete(id);
    }
}
