package com.rentcar.api.domain.customer;


import com.rentcar.api.util.NameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    /**
     * Accent-insensitive, lowercase, whitespace-collapsed form of {@link #fullName}.
     * Populated automatically on persist and update via {@link #populateNormalizedFields()}.
     * Use for search/comparison — do not display to users.
     */
    @Column(name = "full_name_normalized")
    private String fullNameNormalized;

    /**
     * Normalized last-name token extracted from {@link #fullName}.
     * Populated automatically on persist and update via {@link #populateNormalizedFields()}.
     * Used by manage-booking lookup for accent-insensitive lastName matching.
     */
    @Column(name = "last_name_normalized")
    private String lastNameNormalized;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        // Instant.now() is intentional: JPA lifecycle callbacks cannot receive Spring beans,
        // so AppClock cannot be injected here. The JVM is pinned to UTC in RentcarApiApplication,
        // making Instant.now() deterministic across all environments.
        this.createdAt = Instant.now();
        populateNormalizedFields();
    }

    @PreUpdate
    public void preUpdate() {
        populateNormalizedFields();
    }

    /**
     * Derives {@link #fullNameNormalized} and {@link #lastNameNormalized} from {@link #fullName}.
     * Called on every persist and update so that changes to the display name are reflected immediately.
     */
    private void populateNormalizedFields() {
        if (this.fullName != null) {
            this.fullNameNormalized = NameNormalizer.normalize(this.fullName);
            this.lastNameNormalized = NameNormalizer.normalize(NameNormalizer.extractLastName(this.fullName));
        }
    }
}