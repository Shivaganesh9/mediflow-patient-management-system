package com.mediflow.analytics.service;

import com.mediflow.analytics.dto.CreateMetricSnapshotRequest;
import com.mediflow.analytics.entity.MetricSnapshot;
import com.mediflow.analytics.repository.MetricSnapshotRepository;
import com.mediflow.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final MetricSnapshotRepository metricSnapshotRepository;

    @Transactional
    public MetricSnapshot createSnapshot(CreateMetricSnapshotRequest request) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setCategory(request.getCategory());
        snapshot.setMetricName(request.getMetricName());
        snapshot.setValue(request.getValue());
        snapshot.setUnit(request.getUnit());
        return metricSnapshotRepository.save(snapshot);
    }

    public List<MetricSnapshot> getAllSnapshots() {
        return metricSnapshotRepository.findAll();
    }

    public MetricSnapshot getSnapshot(UUID snapshotId) {
        return metricSnapshotRepository.findBySnapshotId(snapshotId)
                .orElseThrow(() -> new ResourceNotFoundException("Metric snapshot not found with id: " + snapshotId));
    }
}
