package pl.football.seeder.random;

import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * Fills an empty database with random but consistent football data from June 2024 to 1 November
 * 2026. The same seed gives the same data. See specyfikacja_seedera.md.
 *
 * <p>Everything that does not depend on results is decided first: squads in every season, coaches,
 * injuries and the date template. Only then are the matches played, one edition after another.
 */
public final class RandomSeeder {

    private final EntityManager em;
    private final long seed;

    public RandomSeeder(EntityManager em, long seed) {
        this.em = em;
        this.seed = seed;
    }

    public void seedAll() {
        Rng rng = new Rng(seed);
        Db db = new Db(em);
        Catalog catalog = Catalog.load();
        Names names = new Names(rng);

        // 1. Editors and dictionaries: everything else refers to them.
        Editors editors = new Editors(db, rng, names);
        editors.createAll();
        Dictionaries dict = new Dictionaries(db, editors, catalog);

        // 2. People and every plan that does not depend on results.
        People people = new People(rng, catalog, dict, names);
        people.createClubs();
        people.createNationalTeams(Competitions.COPA_GROUPS.stream().flatMap(List::stream).toList());
        Transfers transfers = new Transfers(rng);
        transfers.simulate(people.players, people.clubs);
        people.buildRostersAndShirts();
        people.planInjuries();
        people.planCoaches();
        people.createReferees();
        people.persist(db, editors);
        transfers.persist(db, dict, editors);
        db.endOfBatch();

        // 3. The matches, edition after edition.
        new Competitions(rng, db, catalog, dict, editors, people, new MatchEngine(rng, db, dict, editors),
                new Calendar()).runAll();

        // 4. What is known only at "now", then the check that every kind of data is present.
        people.persistInjuriesAndSquads(db, editors);
        db.endOfBatch();
        new CoverageCheck(em).verify();
    }
}
