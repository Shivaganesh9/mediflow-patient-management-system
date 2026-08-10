package com.mediflow.doctor.mapper;

import com.mediflow.doctor.dto.CreateDoctorRequest;
import com.mediflow.doctor.dto.DoctorResponse;
import com.mediflow.doctor.entity.Doctor;
import org.springframework.stereotype.Component;

@Component
public class DoctorMapper {
    public Doctor toEntity(CreateDoctorRequest request) {
        Doctor doctor = new Doctor();
        updateEntity(doctor, request);
        return doctor;
    }

    public void updateEntity(Doctor doctor, CreateDoctorRequest request) {
        doctor.setFirstName(request.getFirstName());
        doctor.setLastName(request.getLastName());
        doctor.setEmail(request.getEmail());
        doctor.setPhone(request.getPhone());
        doctor.setSpecialty(request.getSpecialty());
        doctor.setYearsOfExperience(request.getYearsOfExperience());
        doctor.setDepartment(request.getDepartment());
        doctor.setQualification(request.getQualification());
        doctor.setConsultationFee(request.getConsultationFee());
    }

    public DoctorResponse toResponse(Doctor doctor) {
        return DoctorResponse.builder()
                .doctorId(doctor.getDoctorId())
                .firstName(doctor.getFirstName())
                .lastName(doctor.getLastName())
                .email(doctor.getEmail())
                .phone(doctor.getPhone())
                .specialty(doctor.getSpecialty())
                .yearsOfExperience(doctor.getYearsOfExperience())
                .department(doctor.getDepartment())
                .qualification(doctor.getQualification())
                .consultationFee(doctor.getConsultationFee())
                .createdAt(doctor.getCreatedAt())
                .updatedAt(doctor.getUpdatedAt())
                .version(doctor.getVersion())
                .build();
    }
}
