# Input Sample - API Microservice Documenter

- Service name: Workshop Auth Backend
- Repository path: workshop-auth-backend
- Main runtime and framework: Java 21 + Quarkus
- API exposure: REST
- Entry points: AuthResource, AuthService
- Endpoint sources: src/main/java/com/bcp/workshop/auth
- Data sources: Legacy in-memory/database adapter, HTTP health endpoint
- Auth/Authz model: /auth/login emite JWT HS256 para consumidores internos del laboratorio
- Config and env vars: AUTH_TOKEN_SECRET, AUTH_TOKEN_ISSUER
- Current tests and evidence available: pruebas de resource en src/test y reportes en target/surefire-reports
- Scope of documentation: full service
- Known constraints and assumptions:
  - Entorno de laboratorio
  - No usar secretos reales
  - No exponer datos sensibles
