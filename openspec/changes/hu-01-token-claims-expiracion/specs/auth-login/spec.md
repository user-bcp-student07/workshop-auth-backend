# auth-login spec delta

## ADDED Requirements

### Requirement: Emitir token de login con claims minimos verificables
El sistema MUST emitir en login exitoso un token firmado que contenga los claims sub, client, iat y exp.

#### Scenario: Login exitoso incluye claims obligatorios
- GIVEN credenciales validas y un client valido en la solicitud
- WHEN se invoca POST /auth/login
- THEN la respuesta incluye token, expiresIn, user y debug
- AND el token decodificado/validado expone sub, client, iat y exp

### Requirement: Coherencia temporal entre expiresIn y exp
El sistema MUST garantizar que expiresIn sea coherente con exp - now dentro de una tolerancia documentada.

#### Scenario: Expiracion reportada consistente
- GIVEN un login exitoso
- WHEN se calcula exp - now al momento de emision
- THEN la diferencia entre ese valor y expiresIn se mantiene dentro de la tolerancia documentada

### Requirement: Controlar y validar el origen del claim client
El sistema MUST obtener client desde la solicitud de autenticacion, validarlo y reflejarlo en el token emitido.

Nota: este requirement cambia el baseline de docs/ENDPOINTS.md, donde client era aceptado pero no participaba en la logica de autenticacion.

#### Scenario: Client valido se refleja en el token
- GIVEN una solicitud de login con client valido
- WHEN la autenticacion es exitosa
- THEN el claim client del token coincide con el valor validado de la solicitud

#### Scenario: Client invalido es rechazado
- GIVEN una solicitud de login con client invalido
- WHEN se procesa la autenticacion
- THEN el resultado observable corresponde a error de validacion del request
- AND el codigo HTTP es coherente con la familia 400 del contrato de validacion

### Requirement: Preservar contrato funcional de POST /auth/login
El sistema MUST preservar el comportamiento documentado del endpoint POST /auth/login, salvo cambios explicitamente exigidos por el Issue 11.

#### Scenario: Compatibilidad de respuesta de login exitoso
- GIVEN credenciales validas
- WHEN se invoca POST /auth/login
- THEN la respuesta mantiene los campos funcionales token, expiresIn, user y debug

#### Scenario: Credenciales invalidas mantienen semantica observable
- GIVEN credenciales invalidas
- WHEN se invoca POST /auth/login
- THEN el resultado mantiene la semantica observable definida por el contrato vigente
- AND el codigo HTTP observado es 401

#### Scenario: Request invalido mantiene semantica observable
- GIVEN request nulo o campos username/password vacios
- WHEN se invoca POST /auth/login
- THEN el resultado mantiene la semantica observable de validacion del contrato vigente
- AND el codigo HTTP observado es 400

### Requirement: No exponer secretos ni tokens en logs
El sistema MUST evitar registro de token, secreto o valores sensibles de autenticacion en logs de aplicacion.

#### Scenario: Logs sin datos sensibles durante login
- GIVEN cualquier intento de autenticacion
- WHEN se revisan los logs emitidos por el flujo
- THEN no aparecen token, AUTH_TOKEN_SECRET ni credenciales en texto claro

### Requirement: Token expirado debe ser rechazado en validacion
El sistema MUST rechazar tokens expirados en los puntos de validacion utilizados por el servicio.

#### Scenario: Token expirado no se acepta
- GIVEN un token cuya expiracion exp ya fue superada
- WHEN el token se valida en el flujo correspondiente
- THEN la validacion falla con el resultado observable definido para token invalido/expirado

### Requirement: Registrar decision tecnica sobre mecanismo de firma
El cambio MUST dejar documentada la decision tecnica de mantener HMAC o adoptar una libreria JWT.

#### Scenario: Decision de firma documentada
- GIVEN la implementacion del cambio de seguridad de token
- WHEN se revisa la evidencia del cambio
- THEN existe una decision explicita y verificable sobre HMAC vs JWT y su justificacion
