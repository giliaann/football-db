package pl.football.seeder.random;

import jakarta.persistence.EntityManager;
import pl.football.seeder.entity.AuditedEntity;
import pl.football.seeder.entity.Editor;

import java.time.LocalDateTime;

/**
 * All writes go through here.
 *
 * <p>Inserts run as soon as an entity is saved (IDENTITY keys), so entities must be complete by then.
 */
final class Db {

    private final EntityManager em;

    Db(EntityManager em) {
        this.em = em;
    }

    EntityManager entityManager() {
        return em;
    }

    <T extends AuditedEntity> T save(T entity, Editor by, LocalDateTime at) {
        entity.setLastEditedBy(by);
        entity.setCreatedAt(at);
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(at);
        }
        em.persist(entity);
        return entity;
    }

    /** Detached entities stay usable as references, so the session can be emptied to keep inserts fast. */
    void endOfBatch() {
        em.flush();
        em.clear();
    }
}
