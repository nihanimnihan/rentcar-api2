package com.rentcar.api.domain.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transfer_durations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferDuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int hours;

    @Column(nullable = false)
    private int includedKm;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int displayOrder;
}
