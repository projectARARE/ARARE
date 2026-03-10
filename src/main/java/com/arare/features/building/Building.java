package com.arare.features.building;

import com.arare.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Physical building on a campus.
 * Used to model teacher movement cost and department building preferences.
 */
@Entity
@Table(name = "buildings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Building extends BaseEntity {

    @NotBlank
    @Column(nullable = false)
    private String name;

    /** Human-readable campus location, e.g. "North Campus Block A". */
    @Column
    private String location;
}
