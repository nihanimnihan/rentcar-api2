package com.rentcar.api.domain.insurance;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "insurance_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsurancePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private String nameEn;

    @Column(nullable = false)
    private String nameEs;

    @Column(nullable = false)
    private String nameTr;

    @Column(nullable = false, length = 1000)
    private String descriptionEn;

    @Column(nullable = false, length = 1000)
    private String descriptionEs;

    @Column(nullable = false, length = 1000)
    private String descriptionTr;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerDay;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount;

    @Column(nullable = false)
    private int displayOrder;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean recommended = false;

    @Column
    private String badgeEn;

    @Column
    private String badgeEs;

    @Column
    private String badgeTr;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "insurancePackage", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<InsuranceCoverageItem> coverageItems = new ArrayList<>();

    public void addCoverageItem(InsuranceCoverageItem item) {
        coverageItems.add(item);
        item.setInsurancePackage(this);
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
