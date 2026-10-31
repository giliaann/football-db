package pl.football.seeder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EditionParticipationId implements Serializable {

    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "edition_id")
    private Integer editionId;

    protected EditionParticipationId() {
    }

    public EditionParticipationId(Integer teamId, Integer editionId) {
        this.teamId = teamId;
        this.editionId = editionId;
    }

    public Integer getTeamId() {
        return teamId;
    }

    public Integer getEditionId() {
        return editionId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EditionParticipationId that
                && Objects.equals(teamId, that.teamId)
                && Objects.equals(editionId, that.editionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamId, editionId);
    }
}
