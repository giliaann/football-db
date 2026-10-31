package pl.football.seeder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "EventType")
public class EventType extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_type_id")
    private Integer id;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "score_impact", nullable = false)
    private Short scoreImpact;

    protected EventType() {
    }

    public EventType(String name, String category, Short scoreImpact) {
        this.name = name;
        this.category = category;
        this.scoreImpact = scoreImpact;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Short getScoreImpact() {
        return scoreImpact;
    }

    public void setScoreImpact(Short scoreImpact) {
        this.scoreImpact = scoreImpact;
    }
}
