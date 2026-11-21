package com.mediflow.doctor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.mediflow.common", "com.mediflow.doctor"})
public class DoctorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DoctorServiceApplication.class, args);
    }
}
