package pl.football.seeder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "Edition")
public class Edition extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "edition_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @Column(name = "season", nullable = false, length = 20)
    private String season;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "substitution_limit", nullable = false)
    private Short substitutionLimit;

    @Column(name = "advancement_rules", columnDefinition = "text")
    private String advancementRules;

    protected Edition() {
    }

    public Edition(Competition competition, String season, LocalDate startDate, Short substitutionLimit) {
        this.competition = competition;
        this.season = season;
        this.startDate = startDate;
        this.substitutionLimit = substitutionLimit;
    }

    public Integer getId() {
        return id;
    }

    public Competition getCompetition() {
        return competition;
    }

    public void setCompetition(Competition competition) {
        this.competition = competition;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Short getSubstitutionLimit() {
        return substitutionLimit;
    }

    public void setSubstitutionLimit(Short substitutionLimit) {
        this.substitutionLimit = substitutionLimit;
    }

    public String getAdvancementRules() {
        return advancementRules;
    }

    public void setAdvancementRules(String advancementRules) {
        this.advancementRules = advancementRules;
    }
}
