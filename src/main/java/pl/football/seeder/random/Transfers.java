package pl.football.seeder.random;

import pl.football.seeder.entity.Currency;
import pl.football.seeder.entity.Transfer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Summer transfer windows (July 2025 and July 2026), before each season starts, so a club's squad
 * stays the same during a season. A loan ends by itself in the next window, without a row of its own.
 */
final class Transfers {

    private record Move(PlayerInfo player, TeamInfo from, TeamInfo to, String type, LocalDate date,
                        LocalDate loanEnd, double euros) {
    }

    private static final Map<String, Integer> MINIMUM = Map.of(People.GK, 2, People.DF, 6, People.MF, 6,
            People.FW, 4);
    private static final int MAXIMUM = 30;
    private static final Map<Integer, Double> TYPICAL_FEE_MILLIONS = Map.of(1, 30.0, 2, 8.0, 3, 1.5);

    private final Rng rng;
    private final List<Move> moves = new ArrayList<>();

    Transfers(Rng rng) {
        this.rng = rng;
    }

    void simulate(List<PlayerInfo> players, List<TeamInfo> clubs) {
        for (int s = 1; s < SeedTime.SEASONS.size(); s++) {
            int year = SeedTime.SEASONS.get(s);
            // Everyone starts the window at the club that owns him: loans from last season are over.
            Map<TeamInfo, List<PlayerInfo>> squads = new HashMap<>();
            for (PlayerInfo player : players) {
                TeamInfo owner = player.owners[s - 1];
                if (owner == null) {
                    continue;
                }
                player.clubs[s] = owner;
                player.owners[s] = owner;
                player.joined[s] = player.clubs[s - 1] != owner ? LocalDate.of(year, 7, 1) : player.joined[s - 1];
                squads.computeIfAbsent(owner, c -> new ArrayList<>()).add(player);
            }
            for (PlayerInfo player : rng.shuffled(players)) {
                TeamInfo from = player.clubs[s];
                if (from == null || !rng.chance(0.15) || count(squads.get(from), player.position) <= MINIMUM.get(player.position)) {
                    continue;
                }
                TeamInfo to = destination(from, clubs, squads);
                if (to == null) {
                    continue;
                }
                String type = rng.weighted(List.of("permanent", "free", "loan"),
                        t -> t.equals("permanent") ? 0.6 : 0.2);
                LocalDate date = LocalDate.of(year, 7, rng.between(1, 31));
                squads.get(from).remove(player);
                squads.computeIfAbsent(to, c -> new ArrayList<>()).add(player);
                player.clubs[s] = to;
                player.joined[s] = date;
                LocalDate loanEnd = null;
                if (type.equals("loan")) {
                    loanEnd = LocalDate.of(year + 1, 6, 30);
                } else {
                    player.owners[s] = to;
                }
                moves.add(new Move(player, from, to, type, date, loanEnd, fee(type, player, to, date)));
            }
        }
    }

    void persist(Db db, Dictionaries dict, Editors editors) {
        for (Move move : moves) {
            Transfer transfer = new Transfer(move.player().player, move.from().team, move.to().team,
                    dict.transferTypes.get(move.type()), move.date());
            transfer.setLoanEndDate(move.loanEnd());
            if (move.euros() > 0) {
                Currency currency = dict.currencyFor(move.to().country);
                long amount = Math.round(move.euros() * dict.unitsPerEuro(currency) / 1000) * 1000;
                transfer.setAmount(BigDecimal.valueOf(amount).setScale(2));
                transfer.setCurrency(currency);
            }
            db.save(transfer, editors.transfers(), move.date().atTime(12, 0));
        }
    }

    /** 55% of moves stay in the same country; a club with 30 players takes nobody. */
    private TeamInfo destination(TeamInfo from, List<TeamInfo> clubs, Map<TeamInfo, List<PlayerInfo>> squads) {
        boolean domestic = rng.chance(0.55);
        List<TeamInfo> options = clubs.stream()
                .filter(c -> c != from && (!domestic || c.country.equals(from.country)))
                .filter(c -> squads.getOrDefault(c, List.of()).size() < MAXIMUM)
                .toList();
        return options.isEmpty() ? null : rng.pick(options);
    }

    /** In euros. Grows with the buying club's level, falls with age; loans only sometimes cost anything. */
    private double fee(String type, PlayerInfo player, TeamInfo to, LocalDate date) {
        return switch (type) {
            case "permanent" -> {
                int age = Period.between(player.birthDate(), date).getYears();
                double ageFactor = age < 24 ? 1.3 : age < 30 ? 1.0 : 0.4;
                yield TYPICAL_FEE_MILLIONS.get(to.tier) * 1_000_000 * ageFactor * Math.exp(rng.gaussian(0, 0.6));
            }
            case "loan" -> rng.chance(0.3) ? rng.between(2, 20) * 100_000.0 : 0;
            default -> 0;
        };
    }

    private static long count(List<PlayerInfo> squad, String position) {
        return squad.stream().filter(p -> p.position.equals(position)).count();
    }
}
