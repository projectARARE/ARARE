package com.arare.common;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Common auditing fields shared by all entities.
 * Extend this class in every @Entity to avoid boilerplate.
 *
 * <p>equals/hashCode based on database ID so that Timefold Constraint Stream
 * joiners (which use Objects.equals) correctly match entities that may be
 * different Java instances (e.g. Hibernate proxies vs eager-loaded objects).</p>
 */
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
