CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Relacje geograficzne i organizacyjne
CREATE INDEX IF NOT EXISTS idx_competition_region ON Competition (region_id);
CREATE INDEX IF NOT EXISTS idx_edition_competition ON Edition (competition_id);
CREATE INDEX IF NOT EXISTS idx_stage_edition ON Stage (edition_id);
CREATE INDEX IF NOT EXISTS idx_team_region ON Team (region_id);
CREATE INDEX IF NOT EXISTS idx_team_coach ON Team (coach_id);
CREATE INDEX IF NOT EXISTS idx_stadium_region ON Stadium (region_id);
CREATE INDEX IF NOT EXISTS idx_coach_region ON Coach (region_id);
CREATE INDEX IF NOT EXISTS idx_referee_region ON Referee (region_id);
CREATE INDEX IF NOT EXISTS idx_player_region ON Player (region_id);
CREATE INDEX IF NOT EXISTS idx_player_foot ON Player (foot_id);

-- Tabela Match (mecze)
CREATE INDEX IF NOT EXISTS idx_match_stage ON Match (stage_id);
CREATE INDEX IF NOT EXISTS idx_match_home_team ON Match (home_team_id);
CREATE INDEX IF NOT EXISTS idx_match_away_team ON Match (away_team_id);
CREATE INDEX IF NOT EXISTS idx_match_stadium ON Match (stadium_id);
CREATE INDEX IF NOT EXISTS idx_match_referee ON Match (referee_id);

-- Tabela Appearance (występy zawodników/trenerów w meczu)
CREATE INDEX IF NOT EXISTS idx_appearance_player ON Appearance (player_id) WHERE player_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_appearance_team ON Appearance (team_id);
CREATE INDEX IF NOT EXISTS idx_appearance_coach ON Appearance (coach_id) WHERE coach_id IS NOT NULL;

-- Tabela MatchEvent (zdarzenia boiskowe)
CREATE INDEX IF NOT EXISTS idx_matchevent_appearance ON MatchEvent (appearance_id);
CREATE INDEX IF NOT EXISTS idx_matchevent_event_type ON MatchEvent (event_type_id);

-- Tabela Transfer (transfery)
CREATE INDEX IF NOT EXISTS idx_transfer_player ON Transfer (player_id);
CREATE INDEX IF NOT EXISTS idx_transfer_from_club ON Transfer (from_club_id);
CREATE INDEX IF NOT EXISTS idx_transfer_to_club ON Transfer (to_club_id);
CREATE INDEX IF NOT EXISTS idx_transfer_type ON Transfer (transfer_type_id);
CREATE INDEX IF NOT EXISTS idx_transfer_currency ON Transfer (currency_id);

-- Tabela Injury (kontuzje)
CREATE INDEX IF NOT EXISTS idx_injury_player ON Injury (player_id);

-- Odwrócenie lewego prefiksu dla tabel ze złożonym kluczem głównym
CREATE INDEX IF NOT EXISTS idx_teamsquad_player ON TeamSquad (player_id);
CREATE INDEX IF NOT EXISTS idx_edition_participation_edition ON EditionParticipation (edition_id);

-- Uprawnienia i audyt
CREATE INDEX IF NOT EXISTS idx_permission_assignment_editor ON PermissionAssignment (editor_id);
CREATE INDEX IF NOT EXISTS idx_permission_assignment_permission ON PermissionAssignment (permission_id);


CREATE INDEX IF NOT EXISTS idx_match_finished_analysis
    ON Match (stage_id, scheduled_at)
    INCLUDE (home_team_id, away_team_id, home_goals, away_goals)
    WHERE status = 'finished';

CREATE INDEX IF NOT EXISTS idx_match_referee_finished
    ON Match (referee_id)
    WHERE status = 'finished';

CREATE INDEX IF NOT EXISTS idx_matchevent_active_type
    ON MatchEvent (event_type_id, minute)
    WHERE is_cancelled = false;

CREATE INDEX IF NOT EXISTS idx_injury_date_range
    ON Injury USING gist (player_id, daterange(start_date, end_date, '[]'));

CREATE INDEX IF NOT EXISTS idx_transfer_date_amount
    ON Transfer USING brin (transfer_date) WITH (pages_per_range = 16);

CREATE INDEX IF NOT EXISTS idx_player_name_lower
    ON Player USING gin (last_name gin_trgm_ops, first_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_player_position
    ON Player (primary_position);

VACUUM (ANALYZE) Match;
VACUUM (ANALYZE) Injury;
VACUUM (ANALYZE) Transfer;
VACUUM (ANALYZE) Player;