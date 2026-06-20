package com.rentcar.api.dto.admin;

import java.util.List;

public record AdminSupportRequestPage(
        List<AdminSupportRequestListItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
