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
@Table(name = "MatchEvent")
public class MatchEvent extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_event_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appearance_id", nullable = false)
    private Appearance appearance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(name = "match_period", nullable = false, length = 20)
    private String matchPeriod;

    @Column(name = "minute", nullable = false)
    private Short minute;

    @Column(name = "stoppage_minute")
    private Short stoppageMinute;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    @Column(name = "is_cancelled", nullable = false)
    private Boolean isCancelled;

    @Column(name = "description", length = 255)
    private String description;

    protected MatchEvent() {
    }

    public MatchEvent(Appearance appearance, EventType eventType, String matchPeriod, Short minute, Integer sequenceNo, Boolean isCancelled) {
        this.appearance = appearance;
        this.eventType = eventType;
        this.matchPeriod = matchPeriod;
        this.minute = minute;
        this.sequenceNo = sequenceNo;
        this.isCancelled = isCancelled;
    }

    public Integer getId() {
        return id;
    }

    public Appearance getAppearance() {
        return appearance;
    }

    public void setAppearance(Appearance appearance) {
        this.appearance = appearance;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getMatchPeriod() {
        return matchPeriod;
    }

    public void setMatchPeriod(String matchPeriod) {
        this.matchPeriod = matchPeriod;
    }

    public Short getMinute() {
        return minute;
    }

    public void setMinute(Short minute) {
        this.minute = minute;
    }

    public Short getStoppageMinute() {
        return stoppageMinute;
    }

    public void setStoppageMinute(Short stoppageMinute) {
        this.stoppageMinute = stoppageMinute;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Boolean getIsCancelled() {
        return isCancelled;
    }

    public void setIsCancelled(Boolean isCancelled) {
        this.isCancelled = isCancelled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
