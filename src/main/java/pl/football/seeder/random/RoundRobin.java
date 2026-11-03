package pl.football.seeder.random;

import java.util.ArrayList;
import java.util.List;

/** "Everyone plays everyone" pairings by the circle method (Berger tables). */
final class RoundRobin {

    record Pair(TeamInfo home, TeamInfo away) {
    }

    private RoundRobin() {
    }

    /** Each pair meets once. One team stays in place while the others rotate around it. */
    static List<List<Pair>> once(List<TeamInfo> teams) {
        List<TeamInfo> ring = new ArrayList<>(teams);
        if (ring.size() % 2 == 1) {
            ring.add(null); // A bye: whoever meets it rests that round.
        }
        int n = ring.size();
        List<List<Pair>> rounds = new ArrayList<>();
        for (int r = 0; r < n - 1; r++) {
            List<Pair> round = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                TeamInfo a = ring.get(i);
                TeamInfo b = ring.get(n - 1 - i);
                if (a != null && b != null) {
                    // Alternating sides keeps home and away matches roughly balanced.
                    boolean swap = i == 0 ? r % 2 == 1 : i % 2 == 1;
                    round.add(swap ? new Pair(b, a) : new Pair(a, b));
                }
            }
            rounds.add(round);
            ring.add(1, ring.remove(n - 1));
        }
        return rounds;
    }

    /** Each pair meets twice; the second half of the season mirrors the first with sides swapped. */
    static List<List<Pair>> twice(List<TeamInfo> teams) {
        List<List<Pair>> rounds = new ArrayList<>(once(teams));
        for (List<Pair> round : once(teams)) {
            rounds.add(round.stream().map(p -> new Pair(p.away(), p.home())).toList());
        }
        return rounds;
    }
}
