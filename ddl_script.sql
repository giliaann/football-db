CREATE TABLE Region
(
    region_id              integer GENERATED ALWAYS AS IDENTITY,
    name                   VARCHAR(100) NOT NULL,
    region_type             VARCHAR(20)  NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    last_edited_by_id integer      NOT NULL,
    CONSTRAINT pk_region PRIMARY KEY (region_id),
    CONSTRAINT uk_region_name UNIQUE (name),
    CONSTRAINT ck_region_type CHECK ( region_type IN ('country', 'continent'))
);

CREATE TABLE Competition
(
    competition_id            integer GENERATED ALWAYS AS IDENTITY,
    name                   VARCHAR(120) NOT NULL,
    format                  VARCHAR(20)  NOT NULL,
    level                  VARCHAR(20)  NOT NULL,
    region_id              integer      NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    last_edited_by_id integer      NOT NULL,

    CONSTRAINT pk_competition PRIMARY KEY (competition_id),
    CONSTRAINT ck_competition_format CHECK ( format IN ('league', 'cup', 'mixed') ),
    CONSTRAINT ck_competition_level CHECK ( level IN ('national', 'club') ),
    CONSTRAINT uk_competition_name_region UNIQUE (name, region_id)
);

CREATE TABLE Edition
(
    edition_id               integer GENERATED ALWAYS AS IDENTITY,
    competition_id            integer     NOT NULL,
    season                   VARCHAR(20) NOT NULL,
    start_date        DATE        NOT NULL,
    end_date        DATE,
    substitution_limit             smallint    NOT NULL,
    advancement_rules           TEXT,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_edition PRIMARY KEY (edition_id),
    CONSTRAINT uk_edition_season UNIQUE (competition_id, season),
    CONSTRAINT uk_edition_start_date UNIQUE (competition_id, start_date),
    CONSTRAINT ck_edition_dates CHECK ( end_date IS NULL OR end_date >= start_date ),
    CONSTRAINT ck_edition_substitution_limit CHECK ( substitution_limit >= 0 )
);

CREATE TABLE Stage
(
    stage_id                integer GENERATED ALWAYS AS IDENTITY,
    edition_id               integer     NOT NULL,
    name                   VARCHAR(60) NOT NULL,
    stage_type               VARCHAR(20) NOT NULL,
    stage_order               smallint    NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_stage PRIMARY KEY (stage_id),
    CONSTRAINT uk_stage_name UNIQUE (edition_id, name),
    CONSTRAINT uk_stage_order UNIQUE (edition_id, stage_order),
    CONSTRAINT ck_stage_type CHECK ( stage_type IN ('league', 'group', 'knockout') ),
    CONSTRAINT ck_stage_order CHECK ( stage_order > 0 )
);

CREATE TABLE Team
(
    team_id              integer GENERATED ALWAYS AS IDENTITY,
    name                   VARCHAR(120) NOT NULL,
    short_name          VARCHAR(10),
    team_type             VARCHAR(20)  NOT NULL,
    founded_year           smallint      ,
    region_id              integer      NOT NULL,
    coach_id              integer,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    last_edited_by_id integer      NOT NULL,
    CONSTRAINT pk_team PRIMARY KEY (team_id),
    CONSTRAINT uk_team_name UNIQUE (name),
    CONSTRAINT ck_team_type CHECK ( team_type IN ('club', 'national_team') )
);

CREATE TABLE EditionParticipation
(
    team_id              integer   NOT NULL,
    edition_id               integer   NOT NULL,
    points                  smallint  NOT NULL DEFAULT 0,
    goals_for          smallint  NOT NULL DEFAULT 0,
    goals_against         smallint  NOT NULL DEFAULT 0,
    stage_reached         VARCHAR(60),
    final_position         smallint,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    last_edited_by_id integer   NOT NULL,
    CONSTRAINT pk_edition_participation PRIMARY KEY (team_id, edition_id),
    CONSTRAINT ck_edition_participation_points CHECK ( points >= 0 ),
    CONSTRAINT ck_edition_participation_goals_for CHECK ( goals_for >= 0 ),
    CONSTRAINT ck_edition_participation_goals_against CHECK ( goals_against >= 0 ),
    CONSTRAINT ck_edition_participation_position CHECK ( final_position IS NULL OR final_position > 0 )
);

CREATE TABLE Coach
(
    coach_id              integer GENERATED ALWAYS AS IDENTITY,
    first_name                    VARCHAR(60) NOT NULL,
    last_name                VARCHAR(80) NOT NULL,
    birth_date          DATE,
    region_id              integer     NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_coach PRIMARY KEY (coach_id)

);
CREATE TABLE Foot
(
    foot_id                 integer GENERATED ALWAYS AS IDENTITY,
    name                   VARCHAR(20) NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_foot PRIMARY KEY (foot_id),
    CONSTRAINT uk_foot_name UNIQUE (name)
);
CREATE TABLE Player
(
    player_id            integer GENERATED ALWAYS AS IDENTITY,
    first_name                    VARCHAR(60) NOT NULL,
    last_name                VARCHAR(80) NOT NULL,
    birth_date          DATE        NOT NULL,
    primary_position       VARCHAR(20),
    height_cm               smallint,
    foot_id                 integer,
    region_id              integer     NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_player PRIMARY KEY (player_id),
    CONSTRAINT ck_player_position CHECK ( primary_position IS NULL OR primary_position IN ('goalkeeper', 'defender', 'midfielder', 'forward') )

);

CREATE TABLE TeamSquad
(
    team_id              integer   NOT NULL,
    player_id            integer   NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    last_edited_by_id integer   NOT NULL,
    CONSTRAINT pk_team_squad PRIMARY KEY (team_id, player_id)
);

CREATE TABLE Injury
(
    injury_id             integer GENERATED ALWAYS AS IDENTITY,
    player_id            integer      NOT NULL,
    start_date           DATE         NOT NULL,
    end_date              DATE,
    injury_description              VARCHAR(255) NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    last_edited_by_id integer      NOT NULL,
    CONSTRAINT pk_injury PRIMARY KEY (injury_id),
    CONSTRAINT uk_injury_player_start UNIQUE (player_id, start_date),
    CONSTRAINT ck_injury_dates CHECK ( end_date IS NULL OR end_date >= start_date )
);

CREATE TABLE TransferType
(
    transfer_type_id       integer GENERATED ALWAYS AS IDENTITY,
    name                   VARCHAR(40) NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_transfer_type PRIMARY KEY (transfer_type_id),
    CONSTRAINT uk_transfer_type_name UNIQUE (name)
);
CREATE TABLE Currency
(
    currency_id               integer GENERATED ALWAYS AS IDENTITY,
    code                     char(3)     NOT NULL,
    name                   VARCHAR(60) NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_currency PRIMARY KEY (currency_id),
    CONSTRAINT uk_currency_code UNIQUE (code),
    CONSTRAINT uk_currency_name UNIQUE (name)
);

CREATE TABLE Transfer
(
    transfer_id            integer GENERATED ALWAYS AS IDENTITY,
    player_id            integer   NOT NULL,
    from_club_id     integer   NOT NULL,
    to_club_id     integer   NOT NULL,
    transfer_type_id       integer   NOT NULL,
    transfer_date          DATE      NOT NULL,
    loan_end_date DATE,
    amount                   DECIMAL(14, 2),
    currency_id               integer,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    last_edited_by_id integer   NOT NULL,
    CONSTRAINT pk_transfer PRIMARY KEY (transfer_id),
    CONSTRAINT uk_transfer_player_date UNIQUE (player_id, transfer_date),
    CONSTRAINT ck_transfer_different_clubs CHECK ( from_club_id <> to_club_id ),
    CONSTRAINT ck_transfer_loan_dates CHECK ( loan_end_date IS NULL OR loan_end_date >= transfer_date ),
    CONSTRAINT ck_transfer_amount CHECK ( amount IS NULL OR amount >= 0 ),
    CONSTRAINT ck_transfer_currency_required CHECK ( amount IS NULL OR currency_id IS NOT NULL )
);
CREATE TABLE Referee
(
    referee_id             integer GENERATED ALWAYS AS IDENTITY,
    first_name                    VARCHAR(60) NOT NULL,
    last_name                VARCHAR(80) NOT NULL,
    birth_date          DATE,
    region_id              integer     NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_referee PRIMARY KEY (referee_id)

);

CREATE TABLE Stadium
(
    stadium_id             integer GENERATED ALWAYS AS IDENTITY,
    name                   VARCHAR(120) NOT NULL,
    city                  VARCHAR(80)  NOT NULL,
    capacity               integer,
    region_id              integer      NOT NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    last_edited_by_id integer      NOT NULL,
    CONSTRAINT pk_stadium PRIMARY KEY (stadium_id),
    CONSTRAINT uk_stadium_name_city UNIQUE (name, city),
    CONSTRAINT ck_stadium_capacity CHECK ( capacity IS NULL OR capacity > 0 )
);

CREATE TABLE Match
(
    match_id                 integer GENERATED ALWAYS AS IDENTITY,
    stage_id                 integer     NOT NULL,
    home_team_id            integer     NOT NULL,
    away_team_id                 integer     NOT NULL,
    stadium_id              integer     NOT NULL,
    referee_id              integer     NOT NULL,
    scheduled_at                   TIMESTAMP   NOT NULL,
    first_half_start           TIMESTAMP,
    first_half_end          TIMESTAMP,
    second_half_start           TIMESTAMP,
    second_half_end          TIMESTAMP,
    extra_time_first_half_start  TIMESTAMP,
    extra_time_first_half_end TIMESTAMP,
    extra_time_second_half_start  TIMESTAMP,
    extra_time_second_half_end TIMESTAMP,
    finished_at         TIMESTAMP,
    status                   VARCHAR(20) NOT NULL,
    match_period                VARCHAR(20),
    home_goals        smallint    NOT NULL DEFAULT 0,
    away_goals             smallint    NOT NULL DEFAULT 0,
    home_penalties         smallint,
    away_penalties              smallint,
    advancing_side                    VARCHAR(12),
    created_at             TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id  integer     NOT NULL,
    CONSTRAINT pk_match PRIMARY KEY (match_id),
    CONSTRAINT uk_match_stadium_scheduled_at UNIQUE (stadium_id, scheduled_at),
    CONSTRAINT uk_match_referee_scheduled_at UNIQUE (referee_id, scheduled_at),
    CONSTRAINT uk_match_home_team_scheduled_at UNIQUE (home_team_id, scheduled_at),
    CONSTRAINT uk_match_away_team_scheduled_at UNIQUE (away_team_id, scheduled_at),
    CONSTRAINT ck_match_different_teams CHECK ( home_team_id <> away_team_id ),
    CONSTRAINT ck_match_status CHECK ( status IN ('scheduled', 'in_progress', 'finished', 'postponed', 'cancelled') ),
    CONSTRAINT ck_match_period CHECK ( match_period IS NULL OR match_period IN ('first_half', 'half_time', 'second_half', 'extra_time_1', 'extra_time_2', 'penalties') ),
    CONSTRAINT ck_match_period_vs_status CHECK ( (status = 'in_progress' AND match_period IS NOT NULL) OR (status <> 'in_progress' AND match_period IS NULL) ),
    CONSTRAINT ck_match_home_goals CHECK ( home_goals >= 0 ),
    CONSTRAINT ck_match_away_goals CHECK ( away_goals >= 0 ),
    CONSTRAINT ck_match_home_penalties CHECK ( home_penalties IS NULL OR home_penalties >= 0 ),
    CONSTRAINT ck_match_away_penalties CHECK ( away_penalties IS NULL OR away_penalties >= 0 ),
    CONSTRAINT ck_match_advancing_side CHECK ( advancing_side IS NULL OR advancing_side IN ('home', 'away') )
);

CREATE TABLE Appearance
(
    appearance_id              integer GENERATED ALWAYS AS IDENTITY,
    match_id                integer   NOT NULL,
    team_id              integer   NOT NULL,
    player_id            integer,
    coach_id              integer,
    shirt_number          smallint,
    position                 VARCHAR(20),
    is_starting     boolean   NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    last_edited_by_id integer   NOT NULL,
    CONSTRAINT pk_appearance PRIMARY KEY (appearance_id),
    CONSTRAINT uk_appearance_coach UNIQUE (match_id, coach_id),
    CONSTRAINT uk_appearance_player UNIQUE (match_id, player_id),
    CONSTRAINT uk_appearance_shirt_number UNIQUE (match_id, team_id, shirt_number),
    CONSTRAINT ck_appearance_participant CHECK ( (player_id IS NOT NULL AND coach_id IS NULL) OR (player_id IS NULL AND coach_id IS NOT NULL) )


);

CREATE TABLE EventType
(
    event_type_id      integer GENERATED ALWAYS AS IDENTITY,
    name                   VARCHAR(40) NOT NULL,
    category               VARCHAR(20) NOT NULL,
    score_impact          smallint    NOT NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_event_type PRIMARY KEY (event_type_id),
    CONSTRAINT uk_event_type_name UNIQUE (name),
    CONSTRAINT ck_event_type_category CHECK ( category IN ('goal', 'assist', 'card', 'substitution', 'other') ),
    CONSTRAINT ck_event_type_score_impact CHECK ( score_impact IN (-1, 0, 1) )

);

CREATE TABLE MatchEvent
(
    match_event_id           integer GENERATED ALWAYS AS IDENTITY,
    appearance_id              integer     NOT NULL,
    event_type_id      integer     NOT NULL,
    match_period               VARCHAR(20) NOT NULL,
    minute                  smallint    NOT NULL,
    stoppage_minute        smallint,
    sequence_no              integer     NOT NULL,
    recorded_at        TIMESTAMP   NOT NULL DEFAULT now(),
    is_cancelled           boolean     NOT NULL,
    description                    VARCHAR(255),
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_match_event PRIMARY KEY (match_event_id),
    CONSTRAINT uk_match_event_sequence UNIQUE (appearance_id, sequence_no),
    CONSTRAINT ck_match_event_period CHECK ( match_period IN ('first_half', 'second_half', 'extra_time_1', 'extra_time_2', 'penalties') ),
    CONSTRAINT ck_match_event_minute CHECK ( minute >= 0 ),
    CONSTRAINT ck_match_event_stoppage_minute CHECK ( stoppage_minute IS NULL OR stoppage_minute >= 1 ),
    CONSTRAINT ck_match_event_sequence_no CHECK ( sequence_no > 0 )
);
CREATE TABLE Permission
(
    permission_id          integer GENERATED ALWAYS AS IDENTITY,
    role                    VARCHAR(40) NOT NULL,
    edition_id               integer,
    match_id                integer,
    description                    VARCHAR(255),
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP   NOT NULL DEFAULT now(),
    last_edited_by_id integer     NOT NULL,
    CONSTRAINT pk_permission PRIMARY KEY (permission_id),
    CONSTRAINT ck_permission_role CHECK ( role IN ('administrator', 'competition_administrator', 'master_data_editor', 'match_editor', 'transfer_editor', 'injury_editor') ),
    CONSTRAINT ck_permission_scope_exclusive CHECK ( NOT (edition_id IS NOT NULL AND match_id IS NOT NULL) )
);

CREATE TABLE PermissionAssignment
(
    permission_assignment_id          integer GENERATED ALWAYS AS IDENTITY,
    permission_id          integer   NOT NULL,
    editor_id              integer   NOT NULL,
    granted_by_id      integer   NOT NULL,
    revoked_at          TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP NOT NULL DEFAULT now(),
    last_edited_by_id integer   NOT NULL,
    CONSTRAINT pk_permission_assignment PRIMARY KEY (permission_assignment_id),
    CONSTRAINT uk_permission_assignment UNIQUE (editor_id, permission_id, created_at),
    CONSTRAINT ck_permission_assignment_dates CHECK ( revoked_at IS NULL OR revoked_at >= created_at )
);

CREATE TABLE Editor
(
    editor_id                integer GENERATED ALWAYS AS IDENTITY,
    first_name                      VARCHAR(60)  NOT NULL,
    last_name                  VARCHAR(80)  NOT NULL,
    login                     VARCHAR(40)  NOT NULL,
    email                     VARCHAR(120) NOT NULL,
    password_hash                VARCHAR(255) NOT NULL,
    is_blocked           boolean      NOT NULL,
    last_login_at TIMESTAMP,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT now(),
    last_edited_by_id   integer      NOT NULL,
    CONSTRAINT pk_editor PRIMARY KEY (editor_id),
    CONSTRAINT uk_editor_login UNIQUE (login),
    CONSTRAINT uk_editor_email UNIQUE (email)

);

ALTER TABLE Competition
    ADD CONSTRAINT fk_competition_region FOREIGN KEY (region_id)
        REFERENCES Region (region_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Edition
    ADD CONSTRAINT fk_edition_competition FOREIGN KEY (competition_id)
        REFERENCES Competition (competition_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Stage
    ADD CONSTRAINT fk_stage_edition FOREIGN KEY (edition_id)
        REFERENCES Edition (edition_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Team
    ADD CONSTRAINT fk_team_region FOREIGN KEY (region_id)
        REFERENCES Region (region_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Team
    ADD CONSTRAINT fk_team_coach FOREIGN KEY (coach_id)
        REFERENCES Coach (coach_id)
        ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE EditionParticipation
    ADD CONSTRAINT fk_edition_participation_team FOREIGN KEY (team_id)
        REFERENCES Team (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE EditionParticipation
    ADD CONSTRAINT fk_edition_participation_edition FOREIGN KEY (edition_id)
        REFERENCES Edition (edition_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Coach
    ADD CONSTRAINT fk_coach_region FOREIGN KEY (region_id)
        REFERENCES Region (region_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Player
    ADD CONSTRAINT fk_player_foot FOREIGN KEY (foot_id)
        REFERENCES Foot (foot_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Player
    ADD CONSTRAINT fk_player_region FOREIGN KEY (region_id)
        REFERENCES Region (region_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE TeamSquad
    ADD CONSTRAINT fk_team_squad_team FOREIGN KEY (team_id)
        REFERENCES Team (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE TeamSquad
    ADD CONSTRAINT fk_team_squad_player FOREIGN KEY (player_id)
        REFERENCES Player (player_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Injury
    ADD CONSTRAINT fk_injury_player FOREIGN KEY (player_id)
        REFERENCES Player (player_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Transfer
    ADD CONSTRAINT fk_transfer_player FOREIGN KEY (player_id)
        REFERENCES Player (player_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Transfer
    ADD CONSTRAINT fk_transfer_from_club FOREIGN KEY (from_club_id)
        REFERENCES Team (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Transfer
    ADD CONSTRAINT fk_transfer_to_club FOREIGN KEY (to_club_id)
        REFERENCES Team (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Transfer
    ADD CONSTRAINT fk_transfer_type FOREIGN KEY (transfer_type_id)
        REFERENCES TransferType (transfer_type_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Transfer
    ADD CONSTRAINT fk_transfer_currency FOREIGN KEY (currency_id)
        REFERENCES Currency (currency_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Referee
    ADD CONSTRAINT fk_referee_region FOREIGN KEY (region_id)
        REFERENCES Region (region_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Stadium
    ADD CONSTRAINT fk_stadium_region FOREIGN KEY (region_id)
        REFERENCES Region (region_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Match
    ADD CONSTRAINT fk_match_stage FOREIGN KEY (stage_id)
        REFERENCES Stage (stage_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Match
    ADD CONSTRAINT fk_match_home_team FOREIGN KEY (home_team_id)
        REFERENCES Team (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Match
    ADD CONSTRAINT fk_match_away_team FOREIGN KEY (away_team_id)
        REFERENCES Team (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Match
    ADD CONSTRAINT fk_match_stadium FOREIGN KEY (stadium_id)
        REFERENCES Stadium (stadium_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Match
    ADD CONSTRAINT fk_match_referee FOREIGN KEY (referee_id)
        REFERENCES Referee (referee_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Appearance
    ADD CONSTRAINT fk_appearance_match FOREIGN KEY (match_id)
        REFERENCES Match (match_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Appearance
    ADD CONSTRAINT fk_appearance_team FOREIGN KEY (team_id)
        REFERENCES Team (team_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Appearance
    ADD CONSTRAINT fk_appearance_player FOREIGN KEY (player_id)
        REFERENCES Player (player_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Appearance
    ADD CONSTRAINT fk_appearance_coach FOREIGN KEY (coach_id)
        REFERENCES Coach (coach_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE MatchEvent
    ADD CONSTRAINT fk_match_event_appearance FOREIGN KEY (appearance_id)
        REFERENCES Appearance (appearance_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE MatchEvent
    ADD CONSTRAINT fk_match_event_type FOREIGN KEY (event_type_id)
        REFERENCES EventType (event_type_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Permission
    ADD CONSTRAINT fk_permission_edition FOREIGN KEY (edition_id)
        REFERENCES Edition (edition_id)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE Permission
    ADD CONSTRAINT fk_permission_match FOREIGN KEY (match_id)
        REFERENCES Match (match_id)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE PermissionAssignment
    ADD CONSTRAINT fk_permission_assignment_permission FOREIGN KEY (permission_id)
        REFERENCES Permission (permission_id)
        ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE PermissionAssignment
    ADD CONSTRAINT fk_permission_assignment_editor FOREIGN KEY (editor_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE PermissionAssignment
    ADD CONSTRAINT fk_permission_assignment_granted_by FOREIGN KEY (granted_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Region
    ADD CONSTRAINT fk_region_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Competition
    ADD CONSTRAINT fk_competition_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Edition
    ADD CONSTRAINT fk_edition_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Stage
    ADD CONSTRAINT fk_stage_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Team
    ADD CONSTRAINT fk_team_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE EditionParticipation
    ADD CONSTRAINT fk_edition_participation_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Coach
    ADD CONSTRAINT fk_coach_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Foot
    ADD CONSTRAINT fk_foot_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Player
    ADD CONSTRAINT fk_player_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE TeamSquad
    ADD CONSTRAINT fk_team_squad_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Injury
    ADD CONSTRAINT fk_injury_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE TransferType
    ADD CONSTRAINT fk_transfer_type_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Currency
    ADD CONSTRAINT fk_currency_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Transfer
    ADD CONSTRAINT fk_transfer_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Referee
    ADD CONSTRAINT fk_referee_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Stadium
    ADD CONSTRAINT fk_stadium_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Match
    ADD CONSTRAINT fk_match_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Appearance
    ADD CONSTRAINT fk_appearance_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE EventType
    ADD CONSTRAINT fk_event_type_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE MatchEvent
    ADD CONSTRAINT fk_match_event_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Permission
    ADD CONSTRAINT fk_permission_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE PermissionAssignment
    ADD CONSTRAINT fk_permission_assignment_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE Editor
    ADD CONSTRAINT fk_editor_last_edited_by FOREIGN KEY (last_edited_by_id)
        REFERENCES Editor (editor_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;
