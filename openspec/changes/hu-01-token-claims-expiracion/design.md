# Design: HU-01 token claims minimos y expiracion coherente

## 1. Contexto tecnico relevante

### 1.1 Baseline actual (legacy)
- El endpoint actual es `POST /auth/login` en `AuthResource`.
- El contrato funcional vigente (docs/ENDPOINTS.md) devuelve `token`, `expiresIn`, `user`, `debug` en login exitoso.
- El token actual no es JWT: se arma como string `username|role|exp|secret` y luego se codifica en Base64 (`LegacyTokenUtil.create`).
- `expiresIn` hoy es fijo en `3600` y no se calcula contra `exp` al momento de responder.
- El campo `client` existe en request (`LoginRequest`) pero no participa en emision/validacion del token.
- Existen validaciones de request para `username/password` y semantica observable de errores: `400` (request invalido) y `401` (credenciales invalidas).

### 1.2 Cambio OpenSpec que gobierna este diseno
Segun `proposal.md` y `specs/auth-login/spec.md`, el cambio exige:
- claims minimos verificables: `sub`, `client`, `iat`, `exp`;
- coherencia temporal entre `expiresIn` y `exp - now` dentro de tolerancia documentada;
- validacion y reflejo de `client` en el token;
- preservacion del contrato funcional externo de `POST /auth/login`;
- no exponer secretos/tokens/credenciales en logs;
- rechazo de token expirado en validacion;
- dejar documentada decision tecnica de mecanismo de firma.

## 2. Goals y non-goals

### 2.1 Goals
- Emitir tokens con estructura de claims verificable (`sub`, `client`, `iat`, `exp`).
- Hacer observable y testeable la coherencia de expiracion (`expiresIn` vs `exp - now`).
- Incorporar `client` en la logica de emision con reglas de validacion explicitas.
- Preservar el contrato externo y semantica de errores del endpoint de login.
- Eliminar exposicion de informacion sensible en logs del flujo de autenticacion.
- Definir decision tecnica para firma/validacion de token sin redisenar toda la aplicacion.

### 2.2 Non-goals
- No redisenar la arquitectura completa de autenticacion/autorizacion.
- No introducir nuevos endpoints.
- No cambiar el modelo de usuarios ni la persistencia legacy (`LegacyDatabase`).
- No cambiar infraestructura de despliegue ni rotacion operativa avanzada de secretos en esta fase.
- No implementar aqui el codigo funcional (este documento solo disena).

## 3. Contratos protegidos

Los siguientes contratos se consideran protegidos y deben mantenerse salvo lo explicitamente requerido por el Issue 11:
- Ruta y metodo: `POST /auth/login`.
- Tipo de respuesta exitosa: JSON con campos funcionales `token`, `expiresIn`, `user`, `debug`.
- Semantica de errores existente:
  - `400` para request invalido (incluyendo validaciones de entrada).
  - `401` para credenciales invalidas.
- Campos `user.username`, `user.displayName`, `user.role` en respuesta exitosa.
- Compatibilidad de consumidores que esperan la forma de respuesta documentada en `docs/ENDPOINTS.md`.

Ajustes permitidos por el cambio:
- Contenido interno del token (pasar de esquema legacy a token firmado con claims minimos).
- Reglas de validacion de `client` con error de familia `400` cuando sea invalido.

## 4. Componentes afectados

### 4.1 Codigo fuente principal
- `src/main/java/com/bcp/workshop/auth/AuthResource.java`
  - valida request y arma respuesta de login.
  - debera delegar emision de token con claims y computo coherente de expiracion.
- `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java`
  - actualmente genera token legacy Base64.
  - punto natural para evolucionar o reemplazar el mecanismo de emision/validacion.
- `src/main/java/com/bcp/workshop/auth/LoginRequest.java`
  - contiene `client`; se mantiene como fuente del claim y de su validacion.

### 4.2 Pruebas y contrato
- `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`
  - ampliar cobertura a escenarios de contrato y seguridad del cambio.
- `docs/ENDPOINTS.md`
  - actualizar descripcion interna del token y notas de seguridad una vez implementado.

## 5. Decisiones tecnicas y alternativas consideradas

### Decision 1: mecanismo de token
- Decision: migrar de token legacy Base64 custom a un token JWT firmado con HMAC (HS256) usando libreria estandar de JWT.
- Justificacion:
  - reduce riesgo de errores criptograficos por implementacion manual;
  - facilita inclusion/lectura estandar de `sub`, `client`, `iat`, `exp`;
  - simplifica validacion de expiracion y pruebas automatizadas de claims;
  - alinea el servicio con practicas comunes del ecosistema Java/Quarkus.

Alternativas consideradas:
- Mantener formato legacy Base64 agregando claims manualmente.
  - Pros: menor esfuerzo inmediato.
  - Contras: alto riesgo de ambiguedad de parsing, sin estandar de firma JWT, peor mantenibilidad.
- Implementar firma manual HMAC sin libreria JWT.
  - Pros: control total del formato.
  - Contras: mayor superficie de bug de seguridad y mayor costo de pruebas.

### Decision 2: fuente de verdad temporal
- Decision: `exp` sera la fuente de verdad y `expiresIn` se derivara al emitir respuesta.
- Regla: `expiresIn = max(0, exp - now_emision)` con tolerancia de reloj definida para pruebas (ejemplo inicial: hasta 2 segundos).
- Justificacion: elimina divergencias entre valor fijo y expiracion real del token.

Alternativas consideradas:
- Mantener `expiresIn` fijo en 3600 sin derivarlo de `exp`.
  - Rechazada por inconsistencia con requirement de coherencia temporal.

### Decision 3: validacion de client
- Decision: `client` pasa a obligatorio para emision del token del cambio HU-01, con validacion explicita de no nulo/no blanco y longitud acotada.
- Justificacion: el requirement exige controlar origen del claim y reflejar valor validado.

Alternativas consideradas:
- Tratar `client` como opcional y usar fallback interno.
  - Rechazada por debilitar trazabilidad del emisor y no cumplir plenamente el requirement.

## 6. Estrategia incremental

1. Caracterizacion previa del baseline (sin cambiar comportamiento).
2. Introduccion de abstraccion de token (interfaz/servicio) para desacoplar `AuthResource` del formato actual.
3. Implementacion de emision JWT firmada con claims minimos.
4. Implementacion/ajuste de validacion de expiracion en puntos de validacion usados por el servicio.
5. Ajuste de calculo de `expiresIn` derivado de `exp`.
6. Endurecimiento de validacion de `client` y manejo de errores en familia 400.
7. Endurecimiento de logging (sin secretos, sin token en claro).
8. Actualizacion de pruebas y documentacion de contrato.

Nota: la estrategia evita un rediseno transversal y concentra cambios en el flujo de login/token.

## 7. Pruebas de caracterizacion necesarias (antes de comportamiento critico)

Antes de modificar la logica critica de token, se deben congelar pruebas que capturen el baseline observable:

- `POST /auth/login` exitoso devuelve `200` y campos `token`, `expiresIn`, `user`, `debug`.
- `user.displayName` se mantiene escapado como en baseline.
- Credenciales invalidas devuelven `401` con semantica actual.
- Request invalido (`null`, `username/password` vacios) devuelve `400`.
- `client` actualmente no altera autenticacion (evidencia de baseline previo al cambio).
- `expiresIn` actualmente fijo (documentar valor observado para comparar luego).
- Logs del flujo actual: registrar evidencia de que no deben contener secretos ni token en claro tras la implementacion.

Estas pruebas permiten detectar regresiones cuando se cambie el mecanismo interno del token.

## 8. Riesgos y trade-offs

- Riesgo de compatibilidad: consumidores que dependan del formato interno legacy del token podrian fallar.
  - Mitigacion: comunicar cambio de formato interno y conservar contrato externo de respuesta.
- Riesgo temporal: diferencias de reloj pueden volver flakes los tests de `expiresIn`.
  - Mitigacion: usar tolerancia temporal acotada y reloj controlable en pruebas.
- Riesgo de adopcion de `client`: clientes actuales que no envian `client` podrian empezar a fallar.
  - Mitigacion: despliegue controlado y validacion explicita de impacto en consumidores del laboratorio.
- Trade-off seguridad vs simplicidad: JWT estandar agrega dependencia, pero reduce riesgo criptografico y deuda tecnica.

## 9. Rollback (cuando aplique)

Si la migracion de token genera incidentes de compatibilidad en laboratorio:
- rollback funcional: volver temporalmente al emisor legacy anterior manteniendo el contrato externo `POST /auth/login`;
- rollback operativo: restaurar configuracion previa de secreto/TTL asociada al flujo legacy;
- condicion de activacion: incremento sostenido de fallos de autenticacion o incompatibilidad de consumidores.

Para minimizar impacto, se recomienda que la implementacion deje conmutacion controlada del emisor (legacy vs nuevo) durante la ventana inicial de estabilizacion.

## 10. Evidencia de implementacion de la decision de firma

Para el Issue 11 se implemento la decision de adoptar JWT firmado con HMAC (HS256) usando libreria estandar de JWT.

Evidencia trazable en codigo:
- `pom.xml`: dependencia `com.auth0:java-jwt`.
- `src/main/java/com/bcp/workshop/auth/TokenService.java`: abstraccion de emision/validacion de token.
- `src/main/java/com/bcp/workshop/auth/JwtTokenService.java`: emision y validacion JWT con claims `sub`, `client`, `iat`, `exp`.
- `src/main/java/com/bcp/workshop/auth/AuthResource.java`: integracion del servicio de token en `POST /auth/login`.
