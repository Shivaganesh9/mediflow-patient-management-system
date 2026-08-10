# Coding Standards

## General Principles
- Write clear, maintainable, and testable code.
- Favor readability over cleverness.
- Keep services focused on a single business capability.
- Follow language-specific conventions and repository standards.

## Backend Standards
- Use Java 21 and Spring Boot 3 conventions.
- Prefer dependency injection and interface-based abstractions.
- Apply layered architecture: controller, service, repository, domain.
- Use consistent exception handling and response envelopes.

## Frontend Standards
- Use TypeScript strict mode.
- Prefer functional components and hooks.
- Keep components small and reusable.
- Centralize API calls and shared constants.

## Testing Standards
- Unit tests for domain and service logic.
- Integration tests for API and persistence layers.
- Contract tests for inter-service communication.

## DevOps Standards
- Containerize all services.
- Keep configuration externalized.
- Use CI/CD pipelines for build, test, and deployment validation.
