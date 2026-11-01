package pl.football.seeder.random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Who meets whom: round-robins, the UEFA league phase, group draws. Nothing about dates or results. */
final class Pairings {

    record Pair(TeamState home, TeamState away) {
    }

    private Pairings() {
    }

    /** The Berger table: every round is a perfect matching and home and away alternate. */
    static List<List<Pair>> singleRoundRobin(List<TeamState> teams) {
        if (teams.size() % 2 != 0) {
            throw new IllegalArgumentException("Round-robin needs an even number of teams");
        }
        int n = teams.size();
        List<TeamState> rotating = new ArrayList<>(teams);
        List<List<Pair>> rounds = new ArrayList<>();
        for (int round = 0; round < n - 1; round++) {
            List<Pair> pairs = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                TeamState a = rotating.get(i);
                TeamState b = rotating.get(n - 1 - i);
                boolean swap = i == 0 ? round % 2 == 1 : i % 2 == 1;
                pairs.add(swap ? new Pair(b, a) : new Pair(a, b));
            }
            rounds.add(pairs);
            Collections.rotate(rotating.subList(1, n), 1);
        }
        return rounds;
    }

    static List<List<Pair>> doubleRoundRobin(List<TeamState> teams) {
        List<List<Pair>> first = singleRoundRobin(teams);
        List<List<Pair>> all = new ArrayList<>(first);
        for (List<Pair> round : first) {
            all.add(round.stream().map(p -> new Pair(p.away(), p.home())).toList());
        }
        return all;
    }

    /**
     * Eight rounds where each club meets eight different clubs, four at home and four away, avoiding
     * compatriots. Eight rounds of a round-robin give the opponents. Walking closed trails through
     * that graph gives every club as many home as away games, because each visit enters and leaves.
     */
    static List<List<Pair>> leaguePhase(List<TeamState> teams, Rng rng) {
        int n = teams.size();
        List<TeamState> indexes = new ArrayList<>(teams);
        List<List<Pair>> allRounds = singleRoundRobin(indexes);
        List<List<Pair>> chosen = rng.sample(allRounds, 8);

        List<TeamState> arranged = new ArrayList<>(teams);
        int[][] edges = edgesOf(chosen, indexes);
        arrangeAvoidingCompatriots(arranged, edges, rng);

        boolean[] used = new boolean[edges.length];
        int[] homeOf = new int[edges.length];
        for (int start = 0; start < n; start++) {
            int at = start;
            boolean moved = true;
            while (moved) {
                moved = false;
                for (int e = 0; e < edges.length; e++) {
                    if (!used[e] && (edges[e][0] == at || edges[e][1] == at)) {
                        used[e] = true;
                        homeOf[e] = at;
                        at = edges[e][0] == at ? edges[e][1] : edges[e][0];
                        moved = true;
                        break;
                    }
                }
            }
        }

        List<List<Pair>> rounds = new ArrayList<>();
        int edge = 0;
        for (List<Pair> round : chosen) {
            List<Pair> pairs = new ArrayList<>();
            for (int i = 0; i < round.size(); i++, edge++) {
                int home = homeOf[edge];
                int away = edges[edge][0] == home ? edges[edge][1] : edges[edge][0];
                pairs.add(new Pair(arranged.get(home), arranged.get(away)));
            }
            rounds.add(pairs);
        }
        return rounds;
    }

    private static int[][] edgesOf(List<List<Pair>> rounds, List<TeamState> indexes) {
        List<int[]> edges = new ArrayList<>();
        for (List<Pair> round : rounds) {
            for (Pair pair : round) {
                edges.add(new int[]{indexes.indexOf(pair.home()), indexes.indexOf(pair.away())});
            }
        }
        return edges.toArray(new int[0][]);
    }

    /** Local search: swap two clubs whenever that does not create more same-country meetings. */
    private static void arrangeAvoidingCompatriots(List<TeamState> teams, int[][] edges, Rng rng) {
        Collections.shuffle(teams, rng.random);
        int conflicts = conflicts(teams, edges);
        for (int attempt = 0; attempt < 200_000 && conflicts > 0; attempt++) {
            int a = rng.random.nextInt(teams.size());
            int b = rng.random.nextInt(teams.size());
            Collections.swap(teams, a, b);
            int after = conflicts(teams, edges);
            if (after <= conflicts) {
                conflicts = after;
            } else {
                Collections.swap(teams, a, b);
            }
        }
    }

    private static int conflicts(List<TeamState> teams, int[][] edges) {
        int count = 0;
        for (int[] edge : edges) {
            if (Objects.equals(teams.get(edge[0]).country, teams.get(edge[1]).country)) {
                count++;
            }
        }
        return count;
    }

    /** Equal groups, with compatriots kept apart wherever the numbers allow it. */
    static List<List<TeamState>> groups(List<TeamState> teams, int groupSize, Rng rng) {
        List<int[]> edges = new ArrayList<>();
        for (int start = 0; start < teams.size(); start += groupSize) {
            for (int i = start; i < start + groupSize; i++) {
                for (int j = i + 1; j < start + groupSize; j++) {
                    edges.add(new int[]{i, j});
                }
            }
        }
        List<TeamState> arranged = new ArrayList<>(teams);
        arrangeAvoidingCompatriots(arranged, edges.toArray(new int[0][]), rng);
        List<List<TeamState>> groups = new ArrayList<>();
        for (int start = 0; start < arranged.size(); start += groupSize) {
            groups.add(new ArrayList<>(arranged.subList(start, start + groupSize)));
        }
        return groups;
    }

    /** Random pairs, trying to keep compatriots and given "same source" teams apart for a real draw. */
    static List<Pair> draw(List<TeamState> teams, Rng rng, java.util.function.BiPredicate<TeamState, TeamState> avoid) {
        List<TeamState> order = rng.shuffled(teams);
        for (int attempt = 0; attempt < 500; attempt++) {
            boolean clean = true;
            for (int i = 0; i + 1 < order.size(); i += 2) {
                clean &= !avoid.test(order.get(i), order.get(i + 1));
            }
            if (clean) {
                break;
            }
            order = rng.shuffled(teams);
        }
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < order.size(); i += 2) {
            pairs.add(new Pair(order.get(i), order.get(i + 1)));
        }
        return pairs;
    }

    /** Seeded against unseeded, with the seeded team named as home. */
    static List<Pair> seededDraw(List<TeamState> seeded, List<TeamState> unseeded, Rng rng,
                                 java.util.function.BiPredicate<TeamState, TeamState> avoid) {
        List<TeamState> order = rng.shuffled(unseeded);
        for (int attempt = 0; attempt < 500; attempt++) {
            boolean clean = true;
            for (int i = 0; i < seeded.size(); i++) {
                clean &= !avoid.test(seeded.get(i), order.get(i));
            }
            if (clean) {
                break;
            }
            order = rng.shuffled(unseeded);
        }
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < seeded.size(); i++) {
            pairs.add(new Pair(seeded.get(i), order.get(i)));
        }
        return pairs;
    }
}
