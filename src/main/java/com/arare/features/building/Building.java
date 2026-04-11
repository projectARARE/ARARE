package com.arare.features.building;

import com.arare.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

// Physical building on a campus.
// Used to model teacher movement cost and department building preferences.
@Entity
@Table(name = "buildings", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column
    private String location;

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (name != null) {
            name = name.trim();
        }
        if (location != null) {
            location = location.trim();
            if (location.isEmpty()) {
                location = null;
            }
        }
    }
}
