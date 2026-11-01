package pl.football.seeder.random;

import pl.football.seeder.entity.Competition;
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
 * Editors and their permissions. Every other class asks this one who may edit a kind of data, so
 * last_edited_by always names someone who held a matching, unrevoked permission at that moment.
 */
final class EditorDirectory {

    private static final int MAX_LOGIN_LENGTH = 40;
    private static final LocalDateTime GLOBAL_GRANT_AT = LocalDateTime.of(2023, 5, 15, 10, 0);

    private final Db db;
    private final Rng rng;
    private final NameFactory names;
    private final Set<String> logins = new HashSet<>(Set.of("system"));
    private final List<Editor> matchEditorPool = new ArrayList<>();
    private final List<Editor> competitionAdminPool = new ArrayList<>();
    private final List<Editor> guestEditors = new ArrayList<>();
    private final List<Editor> masterDataEditors = new ArrayList<>();
    private final List<Editor> transferEditors = new ArrayList<>();
    private final List<Editor> injuryEditors = new ArrayList<>();

    final Editor system;
    private Editor admin;

    EditorDirectory(Db db, Rng rng, NameFactory names) {
        this.db = db;
        this.rng = rng;
        this.names = names;
        system = bootstrapSystemEditor();
    }

    /** Creates the remaining editors and the permissions that are not tied to an edition. */
    void seedEditors() {
        admin = newEditor(false);
        competitionAdminPool.addAll(newEditors(8));
        matchEditorPool.addAll(newEditors(30));
        guestEditors.addAll(newEditors(5));
        masterDataEditors.addAll(newEditors(3));
        transferEditors.addAll(newEditors(3));
        injuryEditors.addAll(newEditors(3));
        newEditors(9); // Accounts that exist but have never been granted anything.
        List<Editor> blocked = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            blocked.add(newEditor(true));
        }

        Permission administrator = permission("administrator", null, null, "Full access to the system");
        assign(administrator, admin, system, GLOBAL_GRANT_AT, null);
        Permission masterData = permission("master_data_editor", null, null, "Editing of teams, people and venues");
        Permission transfers = permission("transfer_editor", null, null, "Editing of transfers");
        Permission injuries = permission("injury_editor", null, null, "Editing of injuries");
        masterDataEditors.forEach(e -> assign(masterData, e, admin, GLOBAL_GRANT_AT.plusHours(1), null));
        transferEditors.forEach(e -> assign(transfers, e, admin, GLOBAL_GRANT_AT.plusHours(1), null));
        injuryEditors.forEach(e -> assign(injuries, e, admin, GLOBAL_GRANT_AT.plusHours(1), null));

        // Blocked accounts lost whatever they had before the simulated history begins.
        for (Editor editor : blocked) {
            assign(rng.pick(List.of(masterData, transfers, injuries)), editor, admin, GLOBAL_GRANT_AT.plusDays(1),
                    GLOBAL_GRANT_AT.plusDays(rng.between(3, 14)));
        }
    }

    Editor masterDataEditor() {
        return rng.pick(masterDataEditors);
    }

    Editor transferEditor() {
        return rng.pick(transferEditors);
    }

    Editor injuryEditor() {
        return rng.pick(injuryEditors);
    }

    /** The editors of one edition, granted a month before it starts. */
    record EditionStaff(Editor administrator, List<Editor> matchEditors) {
    }

    EditionStaff staffFor(Edition edition, Competition competition, LocalDate end) {
        LocalDateTime grantedAt = edition.getStartDate().minusDays(30).atTime(10, 0);
        String title = competition.getName() + " " + edition.getSeason();

        Editor administrator = rng.pick(competitionAdminPool);
        Permission adminPermission = permission("competition_administrator", edition, null, "Management of " + title);
        assign(adminPermission, administrator, admin, grantedAt, null);

        Permission matchPermission = permission("match_editor", edition, null, "Match editing for " + title);
        List<Editor> editors = rng.sample(matchEditorPool, rng.between(3, 5));
        for (Editor editor : editors) {
            assign(matchPermission, editor, administrator, grantedAt.plusHours(1), null);
        }
        // Someone who left the edition's team after it ended, to have revocations in the history.
        if (end != null && end.isBefore(SeedTime.TODAY.minusMonths(2))) {
            Editor former = rng.pick(matchEditorPool);
            if (!editors.contains(former)) {
                assign(matchPermission, former, administrator, grantedAt.plusHours(2), end.plusDays(30).atTime(9, 0));
            }
        }
        return new EditionStaff(administrator, editors);
    }

    Editor pickGuestEditor() {
        return rng.pick(guestEditors);
    }

    /** One editor trusted with one saved match only. The grant is dated before the match it covers. */
    void grantMatch(Editor guest, Match match, String title, Editor grantedBy) {
        LocalDateTime grantedAt = match.getScheduledAt().minusDays(rng.between(1, 5));
        Permission permission = permission("match_editor", null, match, "Match editing for " + title);
        assign(permission, guest, grantedBy, grantedAt, null);
    }

    private List<Editor> newEditors(int count) {
        List<Editor> editors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            editors.add(newEditor(false));
        }
        return editors;
    }

    private Editor newEditor(boolean blocked) {
        String first;
        String last;
        String login;
        do {
            NameFactory.Person person = names.editor();
            first = person.firstName();
            last = person.lastName();
            login = (ascii(first) + "." + ascii(last)).toLowerCase();
            login = login.substring(0, Math.min(login.length(), MAX_LOGIN_LENGTH));
        } while (!logins.add(login));

        // A hash-shaped value of nothing: seeded editors cannot log in.
        Editor editor = new Editor(first, last, login, login + "@example.com", rng.faker.hashing().sha256(), blocked);
        LocalDateTime createdAt = LocalDate.of(2023, 1, 1).plusDays(rng.between(0, 120)).atTime(rng.between(8, 17), 0);
        editor.setLastLoginAt(blocked
                ? createdAt.plusDays(rng.between(1, 10))
                : SeedTime.NOW.minusDays(rng.between(0, 60)).minusMinutes(rng.between(1, 1440)));
        return db.save(editor, system, createdAt);
    }

    private Permission permission(String role, Edition edition, Match match, String description) {
        Permission permission = new Permission(role);
        permission.setEdition(edition);
        permission.setMatch(match);
        permission.setDescription(description);
        LocalDateTime at = edition != null ? edition.getStartDate().minusDays(31).atTime(9, 0)
                : match != null ? match.getScheduledAt().minusDays(6) : GLOBAL_GRANT_AT;
        return db.save(permission, edition == null && match == null ? system : admin, at);
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

    /**
     * The first editor has to reference itself through a NOT NULL, non-deferrable foreign key, which
     * persist() cannot do because the id is only known after the insert. Taking the id from the
     * sequence up front lets one statement use it for both columns.
     */
    private Editor bootstrapSystemEditor() {
        String hash = rng.faker.hashing().sha256();
        LocalDateTime createdAt = LocalDateTime.of(2023, 1, 1, 0, 0);
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
                .setParameter(5, hash)
                .setParameter(6, createdAt)
                .getSingleResult();
        return db.entityManager().find(Editor.class, id.intValue());
    }

    private static String ascii(String text) {
        String stripped = text.replace('ł', 'l').replace('Ł', 'L').replace('ı', 'i');
        return Normalizer.normalize(stripped, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z]", "");
    }
}
