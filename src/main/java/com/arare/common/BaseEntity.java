package com.arare.common;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// Common auditing fields shared by all entities.
// Extend this class in every @Entity to avoid boilerplate.
// <p>Equality is based on the database ID so that Timefold Constraint Stream
// joiners (which use Objects.equals) correctly match entities that may be
// different Java instances (e.g. Hibernate proxies vs eager-loaded objects).
// Two unpersisted (id == null) instances are deliberately NOT considered equal.</p>
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        if (id != null && that.id != null) return id.equals(that.id);
        return false; // two unpersisted (null id) instances are NOT equal
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }
}
