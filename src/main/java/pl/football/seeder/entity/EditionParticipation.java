package pl.football.seeder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "EditionParticipation")
public class EditionParticipation extends AuditedEntity {

    @EmbeddedId
    private EditionParticipationId id;

    @MapsId("teamId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @MapsId("editionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id")
    private Edition edition;

    @Column(name = "points", nullable = false)
    private Short points = (short) 0;

    @Column(name = "goals_for", nullable = false)
    private Short goalsFor = (short) 0;

    @Column(name = "goals_against", nullable = false)
    private Short goalsAgainst = (short) 0;

    @Column(name = "stage_reached", length = 60)
    private String stageReached;

    @Column(name = "final_position")
    private Short finalPosition;

    protected EditionParticipation() {
    }

    public EditionParticipation(Team team, Edition edition) {
        this.id = new EditionParticipationId(team.getId(), edition.getId());
        this.team = team;
        this.edition = edition;
    }

    public EditionParticipationId getId() {
        return id;
    }

    public Team getTeam() {
        return team;
    }

    public Edition getEdition() {
        return edition;
    }

    public Short getPoints() {
        return points;
    }

    public void setPoints(Short points) {
        this.points = points;
    }

    public Short getGoalsFor() {
        return goalsFor;
    }

    public void setGoalsFor(Short goalsFor) {
        this.goalsFor = goalsFor;
    }

    public Short getGoalsAgainst() {
        return goalsAgainst;
    }

    public void setGoalsAgainst(Short goalsAgainst) {
        this.goalsAgainst = goalsAgainst;
    }

    public String getStageReached() {
        return stageReached;
    }

    public void setStageReached(String stageReached) {
        this.stageReached = stageReached;
    }

    public Short getFinalPosition() {
        return finalPosition;
    }

    public void setFinalPosition(Short finalPosition) {
        this.finalPosition = finalPosition;
    }
}
