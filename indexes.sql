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


-- 2. INDEKSY CZĘŚCIOWE (PARTIAL INDEXES) - Optymalizacja pod analitykę
CREATE INDEX IF NOT EXISTS idx_match_finished_analysis 
    ON Match (stage_id, scheduled_at) 
    WHERE status = 'finished';

-- Szybka analiza dyscyplinarna sędziów (zapytanie p_referee_strictness.sql)
CREATE INDEX IF NOT EXISTS idx_match_referee_finished 
    ON Match (referee_id) 
    WHERE status = 'finished';

-- Zdarzenia meczowe, które nie zostały anulowane przez VAR/sędziego
CREATE INDEX IF NOT EXISTS idx_matchevent_active_type 
    ON MatchEvent (event_type_id, minute) 
    WHERE is_cancelled = false;



CREATE INDEX IF NOT EXISTS idx_injury_date_range 
    ON Injury (player_id, start_date, end_date);

CREATE INDEX IF NOT EXISTS idx_transfer_date_amount 
    ON Transfer (transfer_date, amount);

CREATE INDEX IF NOT EXISTS idx_player_name_lower 
    ON Player (LOWER(last_name), LOWER(first_name));

CREATE INDEX IF NOT EXISTS idx_player_position 
    ON Player (primary_position);
