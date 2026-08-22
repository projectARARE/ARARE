package com.arare.features.institute;

import com.arare.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

// A constituent institute/college within the university. Most deployments
// have exactly one; universities with multiple constituent institutes each
// get their own institute row with independent departments, while sharing
// the parent university's term calendar.
@Entity
@Table(name = "institutes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Institute extends BaseEntity {

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, unique = true)
    private String name;

    @NotBlank
    @Size(max = 20)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$")
    @Column(nullable = false, unique = true)
    private String code;

    @Size(max = 255)
    @Column
    private String description;

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (name != null) {
            name = name.trim();
        }
        if (code != null) {
            code = code.trim().toUpperCase();
        }
        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }
    }
}
