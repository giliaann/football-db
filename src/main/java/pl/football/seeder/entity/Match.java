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

import java.time.LocalDateTime;

@Entity
@Table(name = "Match")
public class Match extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_id", nullable = false)
    private Stage stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stadium_id", nullable = false)
    private Stadium stadium;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referee_id", nullable = false)
    private Referee referee;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "first_half_start")
    private LocalDateTime firstHalfStart;

    @Column(name = "first_half_end")
    private LocalDateTime firstHalfEnd;

    @Column(name = "second_half_start")
    private LocalDateTime secondHalfStart;

    @Column(name = "second_half_end")
    private LocalDateTime secondHalfEnd;

    @Column(name = "extra_time_first_half_start")
    private LocalDateTime extraTimeFirstHalfStart;

    @Column(name = "extra_time_first_half_end")
    private LocalDateTime extraTimeFirstHalfEnd;

    @Column(name = "extra_time_second_half_start")
    private LocalDateTime extraTimeSecondHalfStart;

    @Column(name = "extra_time_second_half_end")
    private LocalDateTime extraTimeSecondHalfEnd;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "match_period", length = 20)
    private String matchPeriod;

    @Column(name = "home_goals", nullable = false)
    private Short homeGoals = (short) 0;

    @Column(name = "away_goals", nullable = false)
    private Short awayGoals = (short) 0;

    @Column(name = "home_penalties")
    private Short homePenalties;

    @Column(name = "away_penalties")
    private Short awayPenalties;

    @Column(name = "advancing_side", length = 12)
    private String advancingSide;

    protected Match() {
    }

    public Match(Stage stage, Team homeTeam, Team awayTeam, Stadium stadium, Referee referee, LocalDateTime scheduledAt, String status) {
        this.stage = stage;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.stadium = stadium;
        this.referee = referee;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public void setHomeTeam(Team homeTeam) {
        this.homeTeam = homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public void setAwayTeam(Team awayTeam) {
        this.awayTeam = awayTeam;
    }

    public Stadium getStadium() {
        return stadium;
    }

    public void setStadium(Stadium stadium) {
        this.stadium = stadium;
    }

    public Referee getReferee() {
        return referee;
    }

    public void setReferee(Referee referee) {
        this.referee = referee;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public LocalDateTime getFirstHalfStart() {
        return firstHalfStart;
    }

    public void setFirstHalfStart(LocalDateTime firstHalfStart) {
        this.firstHalfStart = firstHalfStart;
    }

    public LocalDateTime getFirstHalfEnd() {
        return firstHalfEnd;
    }

    public void setFirstHalfEnd(LocalDateTime firstHalfEnd) {
        this.firstHalfEnd = firstHalfEnd;
    }

    public LocalDateTime getSecondHalfStart() {
        return secondHalfStart;
    }

    public void setSecondHalfStart(LocalDateTime secondHalfStart) {
        this.secondHalfStart = secondHalfStart;
    }

    public LocalDateTime getSecondHalfEnd() {
        return secondHalfEnd;
    }

    public void setSecondHalfEnd(LocalDateTime secondHalfEnd) {
        this.secondHalfEnd = secondHalfEnd;
    }

    public LocalDateTime getExtraTimeFirstHalfStart() {
        return extraTimeFirstHalfStart;
    }

    public void setExtraTimeFirstHalfStart(LocalDateTime extraTimeFirstHalfStart) {
        this.extraTimeFirstHalfStart = extraTimeFirstHalfStart;
    }

    public LocalDateTime getExtraTimeFirstHalfEnd() {
        return extraTimeFirstHalfEnd;
    }

    public void setExtraTimeFirstHalfEnd(LocalDateTime extraTimeFirstHalfEnd) {
        this.extraTimeFirstHalfEnd = extraTimeFirstHalfEnd;
    }

    public LocalDateTime getExtraTimeSecondHalfStart() {
        return extraTimeSecondHalfStart;
    }

    public void setExtraTimeSecondHalfStart(LocalDateTime extraTimeSecondHalfStart) {
        this.extraTimeSecondHalfStart = extraTimeSecondHalfStart;
    }

    public LocalDateTime getExtraTimeSecondHalfEnd() {
        return extraTimeSecondHalfEnd;
    }

    public void setExtraTimeSecondHalfEnd(LocalDateTime extraTimeSecondHalfEnd) {
        this.extraTimeSecondHalfEnd = extraTimeSecondHalfEnd;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMatchPeriod() {
        return matchPeriod;
    }

    public void setMatchPeriod(String matchPeriod) {
        this.matchPeriod = matchPeriod;
    }

    public Short getHomeGoals() {
        return homeGoals;
    }

    public void setHomeGoals(Short homeGoals) {
        this.homeGoals = homeGoals;
    }

    public Short getAwayGoals() {
        return awayGoals;
    }

    public void setAwayGoals(Short awayGoals) {
        this.awayGoals = awayGoals;
    }

    public Short getHomePenalties() {
        return homePenalties;
    }

    public void setHomePenalties(Short homePenalties) {
        this.homePenalties = homePenalties;
    }

    public Short getAwayPenalties() {
        return awayPenalties;
    }

    public void setAwayPenalties(Short awayPenalties) {
        this.awayPenalties = awayPenalties;
    }

    public String getAdvancingSide() {
        return advancingSide;
    }

    public void setAdvancingSide(String advancingSide) {
        this.advancingSide = advancingSide;
    }
}
