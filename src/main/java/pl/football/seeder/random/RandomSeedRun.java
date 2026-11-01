package pl.football.seeder.random;

import jakarta.persistence.EntityManager;

/** Connects the parts in dependency order. Each step only needs what the earlier ones have created. */
public final class RandomSeedRun {

    private final Db db;
    private final Rng rng;

    public RandomSeedRun(EntityManager em, long seed) {
        db = new Db(em);
        rng = new Rng(seed);
    }

    public void run() {
        Catalog catalog = Catalog.load();
        NameFactory names = new NameFactory(rng);

        EditorDirectory editors = new EditorDirectory(db, rng, names);
        editors.seedEditors();
        MasterData master = new MasterData(db, editors.system, catalog);
        db.endOfBatch();

        CareerSimulator careers = new CareerSimulator(rng, catalog, master, names);
        careers.simulate();
        careers.persistPeopleAndTeams(db, editors);

        Officials officials = new Officials(rng, catalog, master, names, db, editors);
        db.endOfBatch();

        Simulation simulation = new Simulation(db);
        MatchSimulator matches = new MatchSimulator(rng, catalog, master, careers, officials, editors, db);
        EditionPlanner planner = new EditionPlanner(rng, catalog, master, careers, editors, db, simulation, matches);
        planner.queueEverything();
        simulation.run();

        for (EditionRun run : planner.runs) {
            fixLeagueStandings(run);
            run.persistParticipation(db);
        }
        db.endOfBatch();
        careers.persistInjuries(db, editors);
        careers.persistSquads(db, editors, planner.lastNationalSquads());
    }

    /** League tables are only final once the edition is over; cup positions are set by the final itself. */
    private void fixLeagueStandings(EditionRun run) {
        if (run.competition.format().equals("league") && run.isOver()) {
            int position = 1;
            for (TeamState team : run.stages.getFirst().ranking()) {
                run.finalPositions.put(team, position++);
            }
        }
    }
}
