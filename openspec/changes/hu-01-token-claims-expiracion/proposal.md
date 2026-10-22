# Propuesta de cambio: HU-01 token con claims minimos y expiracion coherente

## Referencia
- Issue fuente: https://github.com/user-bcp-student07/workshop-auth-backend/issues/11
- Insumo funcional solicitado por el issue: HU-01.md (raiz del repositorio)
- Baseline de contrato a preservar: docs/ENDPOINTS.md (Dia 1)

## Por que importa
El issue compromete endurecer el token de autenticacion para reducir riesgos de suplantacion y fugas de datos sensibles, y para hacer verificable la expiracion reportada al cliente. Esto impacta seguridad, trazabilidad de cliente emisor y calidad de pruebas de autenticacion.

## Alcance
- Definir requisitos observables para que el token emitido incluya claims minimos: sub, client, iat, exp.
- Definir requisitos observables para que expiresIn sea coherente con exp - now dentro de una tolerancia documentada.
- Definir requisitos observables para evitar exposicion de token/secreto en logs.
- Definir criterios verificables para login valido, credenciales invalidas y manejo de token expirado.
- Documentar la decision de arquitectura sobre mantener HMAC o migrar a libreria JWT, sin implementarla en esta fase.

## Fuera de alcance
- Implementacion tecnica en codigo Java/Quarkus.
- Creacion de tasks.md o plan de ejecucion tecnica.
- Cambios de infraestructura, despliegue o rotacion operativa de secretos fuera del laboratorio.
- Redefinir dominios funcionales ajenos a autenticacion (persistencia de usuarios, CORS, otros endpoints).

## Comportamiento que debe preservarse
- El endpoint de autenticacion se mantiene en POST /auth/login.
- La respuesta de login exitoso debe mantener la forma del contrato documentado: token, expiresIn, user y debug.
- El comportamiento de errores de validacion y autenticacion debe mantenerse coherente con el contrato documentado (400 para request invalido y 401 para credenciales invalidas).

## Cambios de comportamiento permitidos por el Issue 11
- El claim client deja de ser solo "aceptado" en request y pasa a participar en la emision/validacion del token, con reglas verificables.
- El contenido interno del token cambia desde el esquema legacy documentado a uno firmado con claims minimos (sub, client, iat, exp), manteniendo el contrato externo de respuesta.

## Riesgos y supuestos
- Supuesto: el campo client se mantiene en el request de POST /auth/login y su validacion se define sin romper consumidores existentes.
- Riesgo: endurecer la validacion de client podria impactar clientes que hoy no lo envian o lo envian sin formato esperado.
- Mitigacion en especificacion: declarar la compatibilidad esperada como requisito verificable y exigir evidencia de contrato.

## Resultado esperado de esta propuesta
Artefactos de especificacion listos para guiar implementacion posterior sin definir aun el diseno tecnico ni tareas de ejecucion.
