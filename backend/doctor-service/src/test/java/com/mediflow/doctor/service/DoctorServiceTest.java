package com.mediflow.doctor.service;

import com.mediflow.doctor.dto.CreateDoctorRequest;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.mapper.DoctorMapper;
import com.mediflow.doctor.repository.DoctorRepository;
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
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorMapper doctorMapper;

    @InjectMocks
    private DoctorService doctorService;

    @Test
    void shouldCreateDoctor() {
        CreateDoctorRequest request = new CreateDoctorRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane.doe@example.com");
        request.setPhone("555-0100");
        request.setSpecialty("Cardiology");
        request.setYearsOfExperience(12);

        Doctor savedDoctor = new Doctor();
        savedDoctor.setFirstName(request.getFirstName());
        savedDoctor.setLastName(request.getLastName());
        savedDoctor.setEmail(request.getEmail());
        savedDoctor.setPhone(request.getPhone());
        savedDoctor.setSpecialty(request.getSpecialty());
        savedDoctor.setYearsOfExperience(request.getYearsOfExperience());

        when(doctorRepository.save(any(Doctor.class))).thenReturn(savedDoctor);
        when(doctorMapper.toEntity(request)).thenReturn(savedDoctor);

        Doctor result = doctorService.createDoctor(request);

        assertNotNull(result);
        assertEquals("Jane", result.getFirstName());
        assertEquals("Cardiology", result.getSpecialty());
        verify(doctorRepository).save(any(Doctor.class));
    }
}
