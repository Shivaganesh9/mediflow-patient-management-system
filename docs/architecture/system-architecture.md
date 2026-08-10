# System Architecture

## Overview
MediFlow follows a layered, event-driven architecture built around a React frontend, a Spring Cloud Gateway, and independently deployable backend microservices.

## Core Components
- Frontend: React + TypeScript SPA
- Edge: Spring Cloud Gateway
- Services: Auth, Patient, Doctor, Appointment, Medical Record, Billing, Notification, Analytics, Pharmacy, Lab
- Messaging: Apache Kafka
- Data: PostgreSQL per service
- Platform: Docker + Kubernetes + CI/CD
