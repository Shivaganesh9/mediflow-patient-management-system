package com.mediflow.analytics.service;

import com.mediflow.analytics.dto.CreateMetricSnapshotRequest;
import com.mediflow.analytics.entity.MetricSnapshot;
import com.mediflow.analytics.repository.MetricSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private MetricSnapshotRepository metricSnapshotRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void shouldCreateMetricSnapshot() {
        CreateMetricSnapshotRequest request = new CreateMetricSnapshotRequest();
        request.setCategory("appointments");
        request.setMetricName("daily-bookings");
        request.setValue(18.0);
        request.setUnit("count");

        MetricSnapshot savedSnapshot = new MetricSnapshot();
        savedSnapshot.setCategory(request.getCategory());
        savedSnapshot.setMetricName(request.getMetricName());
        savedSnapshot.setValue(request.getValue());
        savedSnapshot.setUnit(request.getUnit());

        when(metricSnapshotRepository.save(any(MetricSnapshot.class))).thenReturn(savedSnapshot);

        MetricSnapshot result = analyticsService.createSnapshot(request);

        assertNotNull(result);
        assertEquals("appointments", result.getCategory());
        verify(metricSnapshotRepository).save(any(MetricSnapshot.class));
    }
}
