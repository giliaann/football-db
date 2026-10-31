package pl.football.seeder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.LocalDateTime;

/**
 * Audit columns shared by every table.
 *
 * <p>Hibernate writes every mapped column on insert, so the database's {@code DEFAULT now()} never
 * fires for these fields. The callbacks below fill them in instead. A {@code createdAt} set
 * explicitly is kept, which allows seeding historical data.
 */
@MappedSuperclass
public abstract class AuditedEntity {

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "last_edited_by_id", nullable = false)
    private Editor lastEditedBy;

    @PrePersist
    protected void fillAuditTimestamps() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    protected void touchUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Editor getLastEditedBy() {
        return lastEditedBy;
    }

    public void setLastEditedBy(Editor lastEditedBy) {
        this.lastEditedBy = lastEditedBy;
    }
}
