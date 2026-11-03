package pl.football.seeder.random;

import jakarta.persistence.EntityManager;
import pl.football.seeder.entity.AuditedEntity;
import pl.football.seeder.entity.Editor;

import java.time.LocalDateTime;

/**
 * All writes go through here. Keys are IDENTITY, so every save is an INSERT at once and an entity
 * must be complete when it is saved.
 */
final class Db {

    private final EntityManager em;

    Db(EntityManager em) {
        this.em = em;
    }

    EntityManager entityManager() {
        return em;
    }

    /** Sets the audit columns: who edited the row last and when it was created (and, unless set, updated). */
    <T extends AuditedEntity> T save(T entity, Editor by, LocalDateTime createdAt) {
        entity.setLastEditedBy(by);
        entity.setCreatedAt(createdAt);
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(createdAt);
        }
        em.persist(entity);
        return entity;
    }

    /** Empties the session so it does not grow; detached entities still work as foreign key references. */
    void endOfBatch() {
        em.flush();
        em.clear();
    }
}
