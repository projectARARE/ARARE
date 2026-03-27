package com.arare.features.department;

import com.arare.common.BaseEntity;
import com.arare.features.building.Building;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

// Academic department, e.g. CSE, IT, AI.
// Owns a set of allowed buildings for scheduling purposes.
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department extends BaseEntity {

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, unique = true)
    private String name;

    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    @Column(nullable = false, unique = true)
    private String code;

// Buildings this department is allowed to schedule sessions in.
// Soft constraint: sessions should prefer these buildings.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "department_buildings",
        joinColumns = @JoinColumn(name = "department_id"),
        inverseJoinColumns = @JoinColumn(name = "building_id")
    )
    @Builder.Default
    private List<Building> buildingsAllowed = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (name != null) {
            name = name.trim();
        }
        if (code != null) {
            code = code.trim().toUpperCase();
        }
    }
}
