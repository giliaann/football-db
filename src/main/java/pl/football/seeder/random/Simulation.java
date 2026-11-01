package pl.football.seeder.random;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * The clock of the seeder: everything that happens, in date order. Draws can then use real results,
 * because a later round's fixtures are only queued once the earlier round has been played.
 */
final class Simulation {

    private record Item(LocalDateTime at, long order, Runnable action) {
    }

    private final PriorityQueue<Item> queue = new PriorityQueue<>(
            Comparator.comparing(Item::at).thenComparingLong(Item::order));
    private final Db db;
    private long order;

    Simulation(Db db) {
        this.db = db;
    }

    void at(LocalDateTime when, Runnable action) {
        queue.add(new Item(when, order++, action));
    }

    void run() {
        LocalDate day = null;
        while (!queue.isEmpty()) {
            Item item = queue.poll();
            if (!item.at().toLocalDate().equals(day)) {
                db.endOfBatch();
                day = item.at().toLocalDate();
            }
            item.action().run();
        }
        db.endOfBatch();
    }
}
