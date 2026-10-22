# Tasks: HU-01 token claims minimos y expiracion coherente

## Alcance operativo del checklist
- Unidad de trabajo: GitHub Issue 11.
- Fuente de verdad: proposal.md, specs/auth-login/spec.md y design.md del cambio hu-01-token-claims-expiracion.
- Restriccion: no incluir trabajo secundario fuera del flujo de autenticacion y token de POST /auth/login.

## Fase 0 - Preparacion de pruebas de caracterizacion (antes del cambio funcional)

- [x] T001 Crear prueba de caracterizacion de login exitoso (baseline de contrato).
  - Verifica HTTP 200 y presencia de token, expiresIn, user, debug.
  - Verifica user.username, user.displayName y user.role con semantica actual.
  - Evidencia: test automatizado en AuthResourceTest o clase de test dedicada.

- [x] T002 Crear prueba de caracterizacion de credenciales invalidas.
  - Verifica HTTP 401 y semantica observable de mensaje de error vigente.
  - Evidencia: test automatizado pasando usuario/password invalidos.

- [x] T003 Crear prueba de caracterizacion de request invalido.
  - Verifica HTTP 400 para request nulo o username/password vacios.
  - Evidencia: test automatizado para cada caso invalido relevante.

- [x] T004 Crear prueba de caracterizacion del comportamiento actual de client.
  - Verifica que client es aceptado en request y no altera autenticacion en baseline.
  - Evidencia: test con variaciones de client manteniendo mismo resultado de login.

- [x] T005 Crear prueba de caracterizacion de expiresIn baseline.
  - Verifica valor actual fijo en baseline y lo deja documentado en assertions.
  - Evidencia: test automatizado con assertion explicita de expiresIn.

- [x] T006 Crear validacion de caracterizacion sobre logs del flujo de login.
  - Verifica que la estrategia de test puede detectar exposicion de token, secreto o credenciales.
  - Evidencia: prueba/validacion automatizada o procedimiento reproducible documentado para inspeccion de logs.

## Fase 1 - Implementacion incremental del cambio

- [x] T007 Introducir abstraccion de emision/validacion de token sin romper contrato externo.
  - Objetivo: desacoplar AuthResource del formato token legacy para habilitar migracion controlada.
  - Verificacion: todas las pruebas de caracterizacion siguen en verde tras refactor.

- [x] T008 Implementar emision de token firmado con claims minimos.
  - Incluye claims sub, client, iat, exp segun spec.
  - Verificacion: test de login exitoso valida claims decodificados/validados.

- [x] T009 Implementar coherencia temporal entre expiresIn y exp.
  - Regla: expiresIn derivado de exp - now con tolerancia documentada.
  - Verificacion: test temporal con tolerancia acotada (evitar flakes).

- [x] T010 Implementar validacion de client y reflejo en claim client.
  - client valido se refleja en token.
  - client invalido responde error de validacion en familia 400.
  - Verificacion: tests de client valido/invalido.

- [x] T011 Implementar rechazo de token expirado en puntos de validacion usados por el servicio.
  - Verificacion: test con token vencido y resultado observable de invalido/expirado.

- [x] T012 Endurecer logs del flujo para evitar exposicion sensible.
  - No registrar token, AUTH_TOKEN_SECRET ni credenciales en texto claro.
  - Verificacion: prueba/validacion de logs actualizada y en verde.

## Fase 2 - Validaciones de regresion y cierre de alcance

- [x] T013 Ejecutar suite de pruebas del modulo auth y confirmar no regresion de contrato protegido.
  - Incluye pruebas legacy de 200/400/401 y nuevas pruebas de claims/expiracion/client/logs.
  - Evidencia: salida de test con casos en verde.

- [x] T014 Actualizar documentacion de contrato afectada por el Issue 11.
  - Ajustar docs/ENDPOINTS.md en lo relativo a contenido interno del token, client y notas de seguridad.
  - Verificacion: documentacion consistente con comportamiento implementado y spec.

- [x] T015 Registrar evidencia de decision tecnica del mecanismo de firma.
  - Dejar explicitada la adopcion de JWT firmado HMAC y justificacion en artefactos del cambio.
  - Verificacion: referencia clara y trazable en documentos OpenSpec/codigo asociado.

## Criterio de completitud del Issue 11
- Todas las tareas T001-T015 en estado completado.
- Requirements y escenarios de specs/auth-login/spec.md cubiertos por pruebas/validaciones.
- Contrato externo de POST /auth/login preservado salvo cambios explicitamente permitidos por el Issue 11.
