package pl.football.seeder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TeamSquadId implements Serializable {

    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "player_id")
    private Integer playerId;

    protected TeamSquadId() {
    }

    public TeamSquadId(Integer teamId, Integer playerId) {
        this.teamId = teamId;
        this.playerId = playerId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TeamSquadId that
                && Objects.equals(teamId, that.teamId)
                && Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, playerId);
    }
}
