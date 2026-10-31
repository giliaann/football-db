package pl.football.seeder.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "TeamSquad")
public class TeamSquad extends AuditedEntity {

    @EmbeddedId
    private TeamSquadId id;

    @MapsId("teamId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @MapsId("playerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id")
    private Player player;

    protected TeamSquad() {
    }

    public TeamSquad(Team team, Player player) {
        this.id = new TeamSquadId(team.getId(), player.getId());
        this.team = team;
        this.player = player;
    }

    public TeamSquadId getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public Player getPlayer() {
        return player;
    }
}
