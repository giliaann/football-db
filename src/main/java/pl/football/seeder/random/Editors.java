package pl.football.seeder.random;

import pl.football.seeder.entity.Edition;
import pl.football.seeder.entity.Editor;
import pl.football.seeder.entity.Match;
import pl.football.seeder.entity.Permission;
import pl.football.seeder.entity.PermissionAssignment;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Editors and their permissions. Every other class asks this one who edits a kind of data, so
 * last_edited_by always names someone who held a matching permission at that time.
 */
final class Editors {

    /** The people responsible for one edition. */
    record Staff(Editor administrator, List<Editor> matchEditors) {
    }

    private static final int MAX_LOGIN_LENGTH = 40;

    final Editor system;
    Editor admin;

    private final Db db;
    private final Rng rng;
    private final Names names;
    private final Set<String> logins = new HashSet<>(Set.of("system"));
    private final List<Editor> competitionAdmins = new ArrayList<>();
    private final List<Editor> matchEditors = new ArrayList<>();
    private final List<Editor> guests = new ArrayList<>();
    private final List<Editor> masterData = new ArrayList<>();
    private final List<Editor> transfers = new ArrayList<>();
    private final List<Editor> injuries = new ArrayList<>();

    Editors(Db db, Rng rng, Names names) {
        this.db = db;
        this.rng = rng;
        this.names = names;
        system = bootstrapSystemEditor();
    }

    /** Creates all other accounts and the permissions that are not tied to an edition. */
    void createAll() {
        admin = newEditor(false, true);
        competitionAdmins.addAll(newEditors(5));
        matchEditors.addAll(newEditors(20));
        guests.addAll(newEditors(4));
        masterData.addAll(newEditors(3));
        transfers.addAll(newEditors(2));
        injuries.addAll(newEditors(2));
        for (int i = 0; i < 4; i++) {
            newEditor(false, false); // Accounts that were never granted anything.
        }
        List<Editor> blocked = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            blocked.add(newEditor(true, true));
        }

        LocalDateTime at = SeedTime.GLOBAL_GRANT_AT;
        Permission administrator = permission("administrator", null, null, "Full access to the system", system, at);
        assign(administrator, admin, system, at, null);
        Permission masterDataPermission = permission("master_data_editor", null, null,
                "Editing of teams, people and venues", admin, at.plusMinutes(30));
        Permission transferPermission = permission("transfer_editor", null, null, "Editing of transfers", admin,
                at.plusMinutes(30));
        Permission injuryPermission = permission("injury_editor", null, null, "Editing of injuries", admin,
                at.plusMinutes(30));
        masterData.forEach(e -> assign(masterDataPermission, e, admin, at.plusHours(1), null));
        transfers.forEach(e -> assign(transferPermission, e, admin, at.plusHours(1), null));
        injuries.forEach(e -> assign(injuryPermission, e, admin, at.plusHours(1), null));
        // Blocked accounts lost their permission before they were blocked.
        for (Editor editor : blocked) {
            LocalDateTime granted = at.plusDays(1);
            assign(masterDataPermission, editor, admin, granted, granted.plusDays(rng.between(10, 60)));
        }
    }

    Editor masterData() {
        return rng.pick(masterData);
    }

    Editor transfers() {
        return rng.pick(transfers);
    }

    Editor injuries() {
        return rng.pick(injuries);
    }

    Editor guest() {
        return rng.pick(guests);
    }

    /**
     * The global administrator appoints a competition administrator a month before the edition starts,
     * who then grants match editing for the whole edition to three editors.
     */
    Staff staffFor(Edition edition, String title, LocalDate start, LocalDate end) {
        LocalDateTime grantedAt = start.minusDays(30).atTime(10, 0);
        Editor boss = rng.pick(competitionAdmins);
        Permission bossPermission = permission("competition_administrator", edition, null, "Management of " + title,
                admin, grantedAt.minusMinutes(30));
        assign(bossPermission, boss, admin, grantedAt, null);

        Permission matchPermission = permission("match_editor", edition, null, "Match editing for " + title, boss,
                grantedAt.plusMinutes(30));
        List<Editor> chosen = rng.sample(matchEditors, 3);
        chosen.forEach(e -> assign(matchPermission, e, boss, grantedAt.plusHours(1), null));

        // Someone who helped out and lost access a month after the edition ended, so revocations exist.
        if (end != null && end.plusDays(30).atTime(9, 0).isBefore(SeedTime.NOW)) {
            Editor former = rng.shuffled(matchEditors).stream().filter(e -> !chosen.contains(e)).findFirst()
                    .orElseThrow();
            assign(matchPermission, former, boss, grantedAt.plusHours(2), end.plusDays(30).atTime(9, 0));
        }
        return new Staff(boss, chosen);
    }

    /** One editor trusted with one saved match only. */
    void grantMatch(Editor guest, Match match, String title, Editor grantedBy, LocalDateTime at) {
        Permission permission = permission("match_editor", null, match, "Match editing for " + title, grantedBy,
                at.minusMinutes(30));
        assign(permission, guest, grantedBy, at, null);
    }

    private Permission permission(String role, Edition edition, Match match, String description, Editor by,
                                  LocalDateTime at) {
        Permission permission = new Permission(role);
        permission.setEdition(edition);
        permission.setMatch(match);
        permission.setDescription(description);
        return db.save(permission, by, at);
    }

    private void assign(Permission permission, Editor editor, Editor grantedBy, LocalDateTime at,
                        LocalDateTime revokedAt) {
        PermissionAssignment assignment = new PermissionAssignment(permission, editor, grantedBy);
        assignment.setRevokedAt(revokedAt);
        if (revokedAt != null) {
            assignment.setUpdatedAt(revokedAt);
        }
        db.save(assignment, grantedBy, at);
    }

    private List<Editor> newEditors(int count) {
        List<Editor> editors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            editors.add(newEditor(false, true));
        }
        return editors;
    }

    private Editor newEditor(boolean blocked, boolean active) {
        Names.Person person = names.editor();
        String base = (ascii(person.firstName()) + "." + ascii(person.lastName())).toLowerCase();
        base = base.substring(0, Math.min(base.length(), MAX_LOGIN_LENGTH - 2));
        String login = base;
        for (int n = 2; !logins.add(login); n++) {
            login = base + n;
        }
        // A random value shaped like a SHA-256 hash, but the hash of no known password: nobody can log in.
        Editor editor = new Editor(person.firstName(), person.lastName(), login, login + "@example.com",
                rng.faker.hashing().sha256(), blocked);
        LocalDateTime createdAt = LocalDate.of(2024, 1, 2).plusDays(rng.between(0, 70)).atTime(rng.between(8, 16), 0);
        if (blocked) {
            editor.setLastLoginAt(createdAt.plusDays(rng.between(1, 10)));
        } else if (active || rng.chance(0.5)) {
            editor.setLastLoginAt(SeedTime.NOW.minusDays(rng.between(0, 20)).minusMinutes(rng.between(1, 1440)));
        }
        return db.save(editor, system, createdAt);
    }

    /**
     * The first editor must reference itself through the NOT NULL foreign key last_edited_by_id, which
     * persist() cannot do because the id is only known after the insert. Taking the id from the
     * identity sequence first lets one statement use it in both columns.
     */
    private Editor bootstrapSystemEditor() {
        Number id = (Number) db.entityManager().createNativeQuery("""
                        INSERT INTO editor (editor_id, first_name, last_name, login, email, password_hash,
                                            is_blocked, last_edited_by_id, created_at, updated_at)
                        OVERRIDING SYSTEM VALUE
                        SELECT id, ?1, ?2, ?3, ?4, ?5, false, id, ?6, ?6
                        FROM (SELECT nextval(pg_get_serial_sequence('editor', 'editor_id')) AS id) s
                        RETURNING editor_id
                        """)
                .setParameter(1, "System")
                .setParameter(2, "Seeder")
                .setParameter(3, "system")
                .setParameter(4, "system@example.com")
                .setParameter(5, rng.faker.hashing().sha256())
                .setParameter(6, LocalDateTime.of(2024, 1, 1, 0, 0))
                .getSingleResult();
        return db.entityManager().find(Editor.class, id.intValue());
    }

    private static String ascii(String text) {
        String stripped = text.replace('ł', 'l').replace('Ł', 'L');
        return Normalizer.normalize(stripped, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z]", "");
    }
}
