# API Design

*Status: reflects the implemented codebase (2026-08-13).*

## Standards (implemented)
- REST over HTTP with versioned paths `/api/v1/{resource}`
- Uniform `ApiResponse` envelope `{ success, message, data, errors, timestamp }`; HTTP 201 on create
- JWT bearer auth (per-service filter); RBAC + ownership checks
- Spring Data pagination (page/size/sort; default size 20)
- Central error mapping: 404 not found, 409 conflict, 400 validation/illegal arg, 403 access denied
- OpenAPI via Springdoc per service (`/swagger-ui/**`, `/v3/api-docs/**`)

## See
Full endpoint catalog per service in `../api-design.md` (auth, patient, doctor, appointment, medical-record, billing, notification, analytics) and gateway routes.
