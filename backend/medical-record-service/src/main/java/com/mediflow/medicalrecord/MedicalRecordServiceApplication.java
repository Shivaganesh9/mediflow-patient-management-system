package com.mediflow.medicalrecord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.mediflow.common", "com.mediflow.medicalrecord"})
@EnableScheduling
public class MedicalRecordServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalRecordServiceApplication.class, args);
    }
}
