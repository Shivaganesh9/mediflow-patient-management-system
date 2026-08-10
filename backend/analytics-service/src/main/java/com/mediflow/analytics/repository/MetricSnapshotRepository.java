package com.mediflow.analytics.repository;

import com.mediflow.analytics.entity.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {
    Optional<MetricSnapshot> findBySnapshotId(UUID snapshotId);
}
