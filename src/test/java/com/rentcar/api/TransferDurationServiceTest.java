package com.rentcar.api;

import com.rentcar.api.domain.transfer.TransferDuration;
import com.rentcar.api.dto.transfer.TransferDurationResponse;
import com.rentcar.api.repository.TransferDurationRepository;
import com.rentcar.api.service.TransferDurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TransferDurationService — verifies DTO mapping and ordering
 * without loading the Spring context.
 */
@ExtendWith(MockitoExtension.class)
class TransferDurationServiceTest {

    @Mock
    private TransferDurationRepository transferDurationRepository;

    private TransferDurationService transferDurationService;

    @BeforeEach
    void setUp() {
        transferDurationService = new TransferDurationService(transferDurationRepository);
    }

    @Test
    void getActiveDurations_delegatesToActiveSortedRepositoryMethod() {
        when(transferDurationRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of());

        transferDurationService.getActiveDurations();

        verify(transferDurationRepository).findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Test
    void getActiveDurations_mapsAllFieldsToDto() {
        TransferDuration entity = TransferDuration.builder()
                .id(7L)
                .hours(3)
                .includedKm(90)
                .active(true)
                .displayOrder(3)
                .build();
        when(transferDurationRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(entity));

        List<TransferDurationResponse> result = transferDurationService.getActiveDurations();

        assertThat(result).hasSize(1);
        TransferDurationResponse dto = result.get(0);
        assertThat(dto.id()).isEqualTo(7L);
        assertThat(dto.hours()).isEqualTo(3);
        assertThat(dto.includedKm()).isEqualTo(90);
    }

    @Test
    void getActiveDurations_preservesRepositoryOrder() {
        List<TransferDuration> entities = List.of(
                TransferDuration.builder().id(1L).hours(1).includedKm(30).active(true).displayOrder(1).build(),
                TransferDuration.builder().id(2L).hours(2).includedKm(60).active(true).displayOrder(2).build(),
                TransferDuration.builder().id(3L).hours(3).includedKm(90).active(true).displayOrder(3).build()
        );
        when(transferDurationRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(entities);

        List<TransferDurationResponse> result = transferDurationService.getActiveDurations();

        assertThat(result).extracting(TransferDurationResponse::hours)
                .containsExactly(1, 2, 3);
    }

    @Test
    void getActiveDurations_returnsOnlyWhatRepositoryProvides() {
        // The active filter lives in the repository query; the service must not add its own.
        // We stub with two "active" entries and confirm exactly those two are returned.
        List<TransferDuration> active = List.of(
                TransferDuration.builder().id(1L).hours(1).includedKm(30).active(true).displayOrder(1).build(),
                TransferDuration.builder().id(4L).hours(4).includedKm(120).active(true).displayOrder(4).build()
        );
        when(transferDurationRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(active);

        List<TransferDurationResponse> result = transferDurationService.getActiveDurations();

        assertThat(result).extracting(TransferDurationResponse::id)
                .containsExactly(1L, 4L);
    }

    @Test
    void getActiveDurations_emptyRepository_returnsEmptyList() {
        when(transferDurationRepository.findByActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of());

        List<TransferDurationResponse> result = transferDurationService.getActiveDurations();

        assertThat(result).isEmpty();
    }
}
