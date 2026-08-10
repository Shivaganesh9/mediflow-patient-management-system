package com.mediflow.analytics.entity;

import com.mediflow.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "metric_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class MetricSnapshot extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID snapshotId = UUID.randomUUID();

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String metricName;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private String unit;
}
