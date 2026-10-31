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

@Entity
@Table(name = "Stage")
public class Stage extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "stage_type", nullable = false, length = 20)
    private String stageType;

    @Column(name = "stage_order", nullable = false)
    private Short stageOrder;

    protected Stage() {
    }

    public Stage(Edition edition, String name, String stageType, Short stageOrder) {
        this.edition = edition;
        this.name = name;
        this.stageType = stageType;
        this.stageOrder = stageOrder;
    }

    public Integer getId() {
        return id;
    }

    public Edition getEdition() {
        return edition;
    }

    public void setEdition(Edition edition) {
        this.edition = edition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStageType() {
        return stageType;
    }

    public void setStageType(String stageType) {
        this.stageType = stageType;
    }

    public Short getStageOrder() {
        return stageOrder;
    }

    public void setStageOrder(Short stageOrder) {
        this.stageOrder = stageOrder;
    }
}
