package com.mediflow.doctor.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.doctor.dto.CreateDoctorRequest;
import com.mediflow.doctor.dto.DoctorResponse;
import com.mediflow.doctor.entity.Doctor;
import com.mediflow.doctor.mapper.DoctorMapper;
import com.mediflow.doctor.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    @Transactional
    public Doctor createDoctor(CreateDoctorRequest request) {
        if (doctorRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A doctor with this email already exists");
        }
        Doctor doctor = doctorRepository.save(doctorMapper.toEntity(request));
        log.info("Created doctor with id {}", doctor.getDoctorId());
        return doctor;
    }

    public Page<DoctorResponse> searchDoctors(String search, String department, Pageable pageable) {
        return doctorRepository.search(search, department, pageable).map(doctorMapper::toResponse);
    }

    public Doctor getDoctor(UUID doctorId) {
        return doctorRepository.findByDoctorId(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found with id: " + doctorId));
    }

    @Transactional
    public Doctor updateDoctor(UUID doctorId, CreateDoctorRequest request) {
        Doctor doctor = getDoctor(doctorId);
        if (doctorRepository.existsByEmailAndDoctorIdNot(request.getEmail(), doctorId)) {
            throw new IllegalArgumentException("A doctor with this email already exists");
        }
        doctorMapper.updateEntity(doctor, request);
        Doctor updatedDoctor = doctorRepository.save(doctor);
        log.info("Updated doctor with id {}", doctorId);
        return updatedDoctor;
    }
}
