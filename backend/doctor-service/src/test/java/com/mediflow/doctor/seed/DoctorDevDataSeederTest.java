package com.mediflow.doctor.seed;

import com.mediflow.common.seed.DevSeedData;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.entity.DoctorAvailability;
import com.mediflow.doctor.repository.DoctorAvailabilityRepository;
import com.mediflow.doctor.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorDevDataSeederTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorAvailabilityRepository availabilityRepository;

    @InjectMocks
    private DoctorDevDataSeeder seeder;

    @Test
    void seedCreatesTwoDoctorsAndAvailability() {
        when(doctorRepository.existsByDoctorId(any())).thenReturn(false);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(availabilityRepository.existsById(any())).thenReturn(false);
        when(availabilityRepository.save(any(DoctorAvailability.class))).thenAnswer(inv -> inv.getArgument(0));

        seeder.seed();

        ArgumentCaptor<Doctor> doctorCaptor = ArgumentCaptor.forClass(Doctor.class);
        verify(doctorRepository, times(2)).save(doctorCaptor.capture());

        Set<java.util.UUID> ids = doctorCaptor.getAllValues().stream()
                .map(Doctor::getDoctorId)
                .collect(Collectors.toSet());
        assertTrue(ids.contains(DevSeedData.DOCTOR_USER_ID));
        assertTrue(ids.contains(DevSeedData.SECOND_DOCTOR_ID));

        Doctor first = doctorCaptor.getAllValues().stream()
                .filter(d -> d.getDoctorId().equals(DevSeedData.DOCTOR_USER_ID))
                .findFirst()
                .orElseThrow();
        assertEquals(DevSeedData.DOCTOR_EMAIL, first.getEmail());

        ArgumentCaptor<DoctorAvailability> availabilityCaptor = ArgumentCaptor.forClass(DoctorAvailability.class);
        verify(availabilityRepository, times(2)).save(availabilityCaptor.capture());
        List<DoctorAvailability> availabilities = availabilityCaptor.getAllValues();
        assertTrue(availabilities.stream().allMatch(a -> a.getDoctorId().equals(DevSeedData.DOCTOR_USER_ID)));
        assertEquals(Set.of(DevSeedData.AVAILABILITY_1_ID, DevSeedData.AVAILABILITY_2_ID),
                availabilities.stream().map(DoctorAvailability::getAvailabilityId).collect(Collectors.toSet()));
    }

    @Test
    void seedIsIdempotentWhenDoctorsAndAvailabilityExist() {
        when(doctorRepository.existsByDoctorId(any())).thenReturn(true);
        when(availabilityRepository.existsById(any())).thenReturn(true);

        seeder.seed();
        seeder.seed();

        verify(doctorRepository, never()).save(any(Doctor.class));
        verify(availabilityRepository, never()).save(any(DoctorAvailability.class));
    }
}