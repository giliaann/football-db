package pl.football.seeder.random;

import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that every kind of data the schema allows really occurs at least once, so queries always
 * have something to find. Any missing kind stops the seeder, and the transaction is rolled back.
 */
final class CoverageCheck {

    private final EntityManager em;

    CoverageCheck(EntityManager em) {
        this.em = em;
    }

    void verify() {
        Map<String, String> checks = new LinkedHashMap<>();
        for (String status : List.of("scheduled", "in_progress", "finished", "postponed", "cancelled")) {
            checks.put("match status " + status, "select count(*) from match where status = '" + status + "'");
        }
        for (String period : List.of("first_half", "half_time", "second_half")) {
            checks.put("match running in " + period, "select count(*) from match where match_period = '" + period + "'");
        }
        checks.put("extra time", "select count(*) from match where extra_time_first_half_start is not null");
        checks.put("penalty shoot-out", "select count(*) from match where home_penalties is not null");
        checks.put("home side advancing", "select count(*) from match where advancing_side = 'home'");
        checks.put("away side advancing", "select count(*) from match where advancing_side = 'away'");
        for (String type : List.of("goal", "penalty_goal", "own_goal", "assist", "yellow_card", "red_card",
                "substitution_on", "substitution_off", "foul", "penalty_missed")) {
            checks.put("event " + type, "select count(*) from matchevent e join eventtype t "
                    + "on t.event_type_id = e.event_type_id where t.name = '" + type + "'");
        }
        checks.put("cancelled goal", "select count(*) from matchevent where is_cancelled");
        checks.put("event in added time", "select count(*) from matchevent where stoppage_minute is not null");
        checks.put("shoot-out kick", "select count(*) from matchevent where match_period = 'penalties'");
        for (String type : List.of("permanent", "loan", "free")) {
            checks.put("transfer " + type, "select count(*) from transfer t join transfertype y "
                    + "on y.transfer_type_id = t.transfer_type_id where y.name = '" + type + "'");
        }
        checks.put("transfer with a fee", "select count(*) from transfer where amount is not null");
        checks.put("transfer without a fee", "select count(*) from transfer where amount is null");
        checks.put("loan with an end date", "select count(*) from transfer where loan_end_date is not null");
        checks.put("injury lasting now", "select count(*) from injury where end_date is null");
        checks.put("finished injury", "select count(*) from injury where end_date is not null");
        for (String role : List.of("administrator", "competition_administrator", "master_data_editor",
                "match_editor", "transfer_editor", "injury_editor")) {
            checks.put("permission " + role, "select count(*) from permission where role = '" + role + "'");
        }
        checks.put("global permission", "select count(*) from permission where edition_id is null and match_id is null");
        checks.put("edition permission", "select count(*) from permission where edition_id is not null");
        checks.put("match permission", "select count(*) from permission where match_id is not null");
        checks.put("revoked permission", "select count(*) from permissionassignment where revoked_at is not null");
        checks.put("blocked editor", "select count(*) from editor where is_blocked");
        checks.put("editor who never logged in", "select count(*) from editor where last_login_at is null");
        for (String type : List.of("league", "group", "knockout")) {
            checks.put("stage " + type, "select count(*) from stage where stage_type = '" + type + "'");
        }
        for (String format : List.of("league", "cup", "mixed")) {
            checks.put("competition " + format, "select count(*) from competition where format = '" + format + "'");
        }
        checks.put("national team", "select count(*) from team where team_type = 'national_team'");
        checks.put("edition in progress", "select count(*) from edition where end_date is null");
        checks.put("final position", "select count(*) from editionparticipation where final_position is not null");
        checks.put("no final position", "select count(*) from editionparticipation where final_position is null");
        checks.put("player without height", "select count(*) from player where height_cm is null");
        for (String foot : List.of("right", "left", "both")) {
            checks.put("foot " + foot, "select count(*) from player p join foot f on f.foot_id = p.foot_id "
                    + "where f.name = '" + foot + "'");
        }

        List<String> missing = new ArrayList<>();
        checks.forEach((name, sql) -> {
            if (((Number) em.createNativeQuery(sql).getSingleResult()).longValue() == 0) {
                missing.add(name);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Seeded data is missing: " + String.join(", ", missing));
        }
        System.out.println("Coverage check passed: " + checks.size() + " kinds of data present.");
    }
}
