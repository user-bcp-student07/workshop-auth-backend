# Plan de correccion

## Alcance y contrato

Este plan cubre H-01 a H-07, la seleccion confirmada previamente; todos siguen en estado `Pendiente` en `HALLAZGOS.md`. Cada item tiene un apartado y alcance propios. No se modifica codigo ni `docs/ENDPOINTS.md` en esta etapa.

Preservar `POST /auth/login`, el JSON de solicitud, los estados HTTP, los nombres y tipos de campos de respuesta, `expiresIn` y la estructura de `user`. Antes de cambiar el formato interno del token (H-02) o los valores devueltos en los campos de error 500 (H-05), confirmar que ningun consumidor depende de la codificacion Base64 o de la clase concreta de excepcion que describe hoy `docs/ENDPOINTS.md`. No se inspeccionan otros componentes dentro de este alcance; si esa compatibilidad no se puede confirmar, detener esos cambios y coordinar antes de implementarlos.

Las pruebas de caracterizacion de cada apartado se escriben y ejecutan antes de modificar sus clases. Luego se agregan las pruebas de seguridad especificas descritas en el cambio propuesto.

## Item: H-01 — SQL de autenticacion concatena las credenciales

**Hallazgo:** H-01 de `HALLAZGOS.md`.

**Problema:** `LegacyDatabase.findByCredentials` interpola `username` y `password` en SQL. Una entrada puede alterar la consulta en vez de compararse como un valor.

**Cambio propuesto:** Usar `PreparedStatement` con parametros para usuario y contrasena, conservar la consulta por credenciales y cerrar recursos con `try`-with-resources. Mantener los resultados HTTP existentes para autenticacion correcta o fallida.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java`; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`; nuevo `src/test/java/com/bcp/workshop/auth/LegacyDatabaseTest.java` si se requiere aislar persistencia.

**Caracterizacion antes de tocar:** Cubrir login valido (`200` y estructura de respuesta), credenciales incorrectas (`401`) y que una entrada con caracteres especiales se trate como una credencial literal sin autenticar ni alterar la base. No incluir datos de explotacion en fixtures. Despues, comprobar que la consulta usa parametros y repetir esos casos.

## Item: H-02 — Token Base64 reversible con secreto embebido y sin firma

**Hallazgo:** H-02 de `HALLAZGOS.md`.

**Problema:** El token se puede decodificar, contiene un secreto del codigo y no ofrece firma que permita detectar manipulacion.

**Cambio propuesto:** Emitir un token firmado con claims explicitos de sujeto y rol, y expiracion alineada con `expiresIn`. Usar una implementacion mantenida de JWT/JWS en lugar de criptografia o formato caseros. Revisar primero el arbol de dependencias; agregar una dependencia de construccion/verificacion solo si no existe una alternativa disponible y justificarla en el cambio. La clave se obtendra de configuracion externa, sin secretos reales en el repositorio. Mantener `token` como string y la respuesta HTTP vigente. Aplicar la verificacion de compatibilidad del apartado Alcance antes de cambiar su formato.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` (o una clase de seguridad con nombre acorde a su nueva responsabilidad); `src/main/java/com/bcp/workshop/auth/AuthResource.java`; `src/main/resources/application.properties`; `pom.xml` solo si la revision justifica una dependencia; pruebas en `src/test/java/com/bcp/workshop/auth/`.

**Caracterizacion antes de tocar:** En `AuthResourceTest`, fijar el contrato observable de login exitoso: estado `200`, token no vacio de tipo string, `expiresIn` igual a 3600 y campos actuales de `user` y respuesta. Tras implementar, agregar pruebas que verifiquen firma, claims y rechazo de token expirado o alterado. Nombrar la duracion como constante/configuracion para evitar numeros magicos.

## Item: H-03 — Contrasenas almacenadas en texto plano

**Hallazgo:** H-03 de `HALLAZGOS.md`.

**Problema:** El esquema y los datos iniciales guardan contrasenas literales; la lectura de la base revela credenciales directamente utilizables.

**Cambio propuesto:** Guardar un hash lento con salt (BCrypt es la opcion citada por `docs/GOOD_PRACTICES.md`) y verificar la contrasena ingresada contra el hash. La busqueda de persistencia debe localizar al usuario por nombre; la verificacion del secreto corresponde al servicio de autenticacion, no al SQL. Definir migracion para bases locales ya inicializadas, evitando conservar la contrasena en claro. Revisar dependencias existentes antes de agregar un proveedor de hash; si falta, justificar una dependencia mantenida y compatible.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` y/o el repositorio que lo reemplace; `src/main/java/com/bcp/workshop/auth/AuthResource.java` y el nuevo servicio de autenticacion; `src/main/java/com/bcp/workshop/auth/UserRecord.java` si el modelo necesita dejar de representar la contrasena; `pom.xml` solo si se justifica; pruebas de persistencia y servicio en `src/test/java/com/bcp/workshop/auth/`.

**Caracterizacion antes de tocar:** Fijar que las cuentas de laboratorio validas siguen iniciando sesion (`200`), que una contrasena incorrecta devuelve `401` y que la forma de respuesta no cambia. Agregar una prueba de migracion para una base temporal preexistente y comprobar que el valor persistido ya no coincide con la contrasena en claro y que su verificacion sigue funcionando.

## Item: H-04 — Secreto en codigo y CORS permite cualquier origen

**Hallazgo:** H-04 de `HALLAZGOS.md`.

**Problema:** El secreto esta escrito en una clase Java y la expresion de origen CORS acepta cualquier origen, ampliando quien puede invocar el servicio desde un navegador.

**Cambio propuesto:** Externalizar el secreto mediante configuracion de Quarkus/variable de entorno y definir una lista explicita de origenes permitidos para cada entorno. No incluir secretos reales ni un valor productivo por defecto; usar un valor aislado solo en pruebas. Compartir la misma fuente de configuracion con H-02, evitando duplicar lectura de configuracion.

**Archivos a tocar:** `src/main/resources/application.properties`; `src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java` o la nueva clase de seguridad; configuracion de pruebas aislada y pruebas en `src/test/java/com/bcp/workshop/auth/`.

**Caracterizacion antes de tocar:** Registrar el comportamiento actual de `POST /auth/login` y las cabeceras CORS para una solicitud permitida. Despues, probar que el origen configurado recibe las cabeceras CORS y que un origen no listado no las recibe; verificar tambien que un secreto de prueba permite emitir/verificar tokens y nunca aparece en la respuesta.

## Item: H-05 — Errores internos se devuelven al cliente

**Hallazgo:** H-05 de `HALLAZGOS.md`.

**Problema:** La respuesta 500 expone el mensaje de la excepcion y su clase, revelando detalles internos.

**Cambio propuesto:** Devolver un mensaje generico y estable, conservar el estado `500` y las claves/tipos JSON documentados, y enviar el detalle al logger del servidor sin registrar credenciales ni tokens. Separar el mapeo de error del flujo normal si ello reduce responsabilidades del resource. No agregar una dependencia de logging: usar la infraestructura de Quarkus disponible.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/AuthResource.java` y, si el mapeo queda fuera del resource, un mapper de excepciones nuevo; `src/test/java/com/bcp/workshop/auth/AuthResourceTest.java`.

**Caracterizacion antes de tocar:** Provocar un fallo controlado y fijar el estado `500`, el tipo JSON y la presencia/tipo de las claves `message` y `exception`, sin acoplar la prueba a un texto variable de runtime. Tras el cambio, verificar que el mensaje sea generico, que el valor de `exception` no revele la clase real y que el detalle solo se registre en servidor. Validar con el control de compatibilidad del apartado Alcance.

## Item: H-06 — Versiones requieren verificacion SCA

**Hallazgo:** H-06 de `HALLAZGOS.md`.

**Problema:** El POM fija las versiones indicadas en el reto, pero eso por si solo no prueba una CVE aplicable. Actualizar sin evidencia puede introducir cambios innecesarios o incompatibles; no corregir una advisory aplicable mantiene el riesgo.

**Cambio propuesto:** Obtener el arbol de dependencias efectivo y el resultado de CodeQL/Dependency Review o de la herramienta SCA del proyecto; contrastar versiones, alcance y version corregida de cada advisory. Si una advisory aplica, actualizar a la version corregida compatible o retirar Commons Text solo si su uso puede eliminarse sin duplicar una funcion de seguridad. No silenciar alertas ni hacer cambios de version especulativos.

**Archivos a tocar:** `pom.xml` solo si la evidencia SCA justifica el cambio; `src/main/java/com/bcp/workshop/auth/AuthResource.java` solo si se decide retirar Commons Text y se preserva el comportamiento de `displayName`; prueba correspondiente en `src/test/java/com/bcp/workshop/auth/`.

**Caracterizacion antes de tocar:** No corresponde una prueba funcional para confirmar una CVE. Guardar como baseline el arbol de dependencias y el reporte SCA de las versiones actuales antes de editar el POM. Si se modifica una dependencia, ejecutar el mismo escaneo y `mvn verify`; si no hay advisory aplicable, no cambiar versiones y documentar la evidencia al actualizar el estado del hallazgo.

## Item: H-07 — `AuthResource.login` mezcla responsabilidades

**Hallazgo:** H-07 de `HALLAZGOS.md`.

**Problema:** El resource valida, consulta persistencia, genera token, construye respuesta y maneja excepciones en un metodo anidado. Los cambios de autenticacion afectan directamente el HTTP y dificultan pruebas focalizadas.

**Cambio propuesto:** Separar Resource (HTTP), Service (flujo de autenticacion) y Repository (persistencia), de acuerdo con `docs/GOOD_PRACTICES.md`. Mantener DTOs de entrada/salida y mapear `UserRecord` a la respuesta, sin exponer la entidad. Usar guard clauses, metodos de una responsabilidad y constantes/configuracion con nombre; no duplicar validaciones ni reglas. Realizar la separacion en cambios revisables y coordinar su orden con H-01, H-03 y H-05 para evitar editar dos veces la misma logica.

**Archivos a tocar:** `src/main/java/com/bcp/workshop/auth/AuthResource.java`; `src/main/java/com/bcp/workshop/auth/LegacyDatabase.java` o repositorio nuevo; nuevos `AuthService.java`, interfaz de repositorio y DTOs de salida si son necesarios; `src/main/java/com/bcp/workshop/auth/UserRecord.java` solo si su responsabilidad cambia; pruebas en `src/test/java/com/bcp/workshop/auth/`.

**Caracterizacion antes de tocar:** Ampliar `AuthResourceTest` para fijar respuestas de login valido, solicitud nula, usuario/contrasena vacios y credenciales invalidas, ademas de los campos completos de exito definidos en `docs/ENDPOINTS.md`. Ejecutar esta suite antes de extraer capas y conservarla durante cada paso. Al cerrar, cubrir las reglas del servicio de forma aislada y mantener al menos 85% de cobertura del codigo modificado conforme a `REGLAS.md`.