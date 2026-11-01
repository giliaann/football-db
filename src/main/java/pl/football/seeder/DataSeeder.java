package pl.football.seeder;

import jakarta.persistence.EntityManager;
import pl.football.seeder.random.RandomSeedRun;

/**
 * Fills the database with a realistic football history from 2023 to the end of 2026. The same seed
 * gives the same data. See opis_seedera.md for how the classes in pl.football.seeder.random fit together.
 */
public final class DataSeeder {

    private final EntityManager em;
    private final long seed;

    public DataSeeder(EntityManager em, long seed) {
        this.em = em;
        this.seed = seed;
    }

    public void seedAll() {
        new RandomSeedRun(em, seed).run();
    }
}
