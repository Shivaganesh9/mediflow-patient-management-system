package com.mediflow.analytics.seed;

import com.mediflow.analytics.entity.AppointmentFact;
import com.mediflow.analytics.entity.InvoiceFact;
import com.mediflow.analytics.entity.MedicalRecordFact;
import com.mediflow.analytics.entity.PaymentFact;
import com.mediflow.analytics.entity.ProcessedEvent;
import com.mediflow.analytics.repository.AppointmentFactRepository;
import com.mediflow.analytics.repository.InvoiceFactRepository;
import com.mediflow.analytics.repository.MedicalRecordFactRepository;
import com.mediflow.analytics.repository.PaymentFactRepository;
import com.mediflow.analytics.repository.ProcessedEventRepository;
import com.mediflow.common.seed.DevSeedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsDevDataSeederTest {

    @Mock
    private AppointmentFactRepository appointmentFactRepository;

    @Mock
    private InvoiceFactRepository invoiceFactRepository;

    @Mock
    private PaymentFactRepository paymentFactRepository;

    @Mock
    private MedicalRecordFactRepository medicalRecordFactRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private AnalyticsDevDataSeeder seeder;

    @Test
    void seedCreatesFactsAndLedgerEntries() {
        when(processedEventRepository.existsById(any())).thenReturn(false);
        when(appointmentFactRepository.findByAppointmentId(any())).thenReturn(Optional.empty());
        when(appointmentFactRepository.save(any(AppointmentFact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceFactRepository.findByInvoiceId(any())).thenReturn(Optional.empty());
        when(invoiceFactRepository.save(any(InvoiceFact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentFactRepository.save(any(PaymentFact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicalRecordFactRepository.findByRecordId(any())).thenReturn(Optional.empty());
        when(medicalRecordFactRepository.save(any(MedicalRecordFact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        verify(appointmentFactRepository, times(4)).save(any(AppointmentFact.class));
        verify(invoiceFactRepository, times(2)).save(any(InvoiceFact.class));
        verify(paymentFactRepository, times(1)).save(any(PaymentFact.class));
        verify(medicalRecordFactRepository, times(2)).save(any(MedicalRecordFact.class));
        verify(processedEventRepository, times(9)).save(any(ProcessedEvent.class));

        ArgumentCaptor<AppointmentFact> apptCaptor = ArgumentCaptor.forClass(AppointmentFact.class);
        verify(appointmentFactRepository, times(4)).save(apptCaptor.capture());
        Set<java.util.UUID> apptIds = apptCaptor.getAllValues().stream()
                .map(AppointmentFact::getAppointmentId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(DevSeedData.APPOINTMENT_1_ID, DevSeedData.APPOINTMENT_2_ID,
                DevSeedData.APPOINTMENT_3_ID, DevSeedData.APPOINTMENT_4_ID), apptIds);

        ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository, times(9)).save(eventCaptor.capture());
        assertEquals(9, eventCaptor.getAllValues().stream()
                .map(ProcessedEvent::getEventId).collect(Collectors.toSet()).size());
    }

    @Test
    void seedIsIdempotentWhenLedgerHasEvents() {
        when(processedEventRepository.existsById(any())).thenReturn(true);

        seeder.seed();
        seeder.seed();

        verify(appointmentFactRepository, never()).save(any(AppointmentFact.class));
        verify(invoiceFactRepository, never()).save(any(InvoiceFact.class));
        verify(paymentFactRepository, never()).save(any(PaymentFact.class));
        verify(medicalRecordFactRepository, never()).save(any(MedicalRecordFact.class));
        verify(processedEventRepository, never()).save(any(ProcessedEvent.class));
    }
}