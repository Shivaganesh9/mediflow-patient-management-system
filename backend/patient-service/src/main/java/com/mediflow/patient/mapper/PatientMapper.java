package com.mediflow.patient.mapper;

import com.mediflow.patient.dto.CreatePatientRequest;
import com.mediflow.patient.dto.PatientResponse;
import com.mediflow.patient.entity.Patient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PatientMapper {

    public Patient toEntity(CreatePatientRequest request) {
        Patient patient = new Patient();
        updateEntity(patient, request);
        return patient;
    }

    public void updateEntity(Patient patient, CreatePatientRequest request) {
        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setEmail(request.getEmail());
        patient.setPhone(request.getPhone());
        patient.setDateOfBirth(parseDate(request.getDateOfBirth()));
        patient.setAddress(request.getAddress());
        patient.setMedicalHistory(request.getMedicalHistory());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient.setInsuranceProvider(request.getInsuranceProvider());
        patient.setInsuranceMemberId(request.getInsuranceMemberId());
    }

    public PatientResponse toResponse(Patient patient) {
        return PatientResponse.builder()
                .patientId(patient.getPatientId())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .email(patient.getEmail())
                .phone(patient.getPhone())
                .dateOfBirth(patient.getDateOfBirth())
                .address(patient.getAddress())
                .medicalHistory(patient.getMedicalHistory())
                .emergencyContactName(patient.getEmergencyContactName())
                .emergencyContactPhone(patient.getEmergencyContactPhone())
                .insuranceProvider(patient.getInsuranceProvider())
                .insuranceMemberId(patient.getInsuranceMemberId())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .version(patient.getVersion())
                .build();
    }

    private LocalDate parseDate(String dateOfBirth) {
        return dateOfBirth == null || dateOfBirth.isBlank() ? null : LocalDate.parse(dateOfBirth);
    }
}
