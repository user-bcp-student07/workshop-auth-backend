# Contrato de endpoints

Este documento describe el comportamiento del codigo presente en este repositorio. La ruta de health la expone la extension SmallRye Health; los demas endpoints de esta pagina corresponden al backend.

## `POST /auth/login`

### 1. Que hace

Autentica una cuenta local del workshop y, si las credenciales coinciden, devuelve los datos basicos del usuario junto con un token para el flujo de autenticacion.

### 2. Autenticacion

No requiere autenticacion previa ni un header `Authorization`. Envia `Content-Type: application/json`; la respuesta se produce como `application/json`. La ruta y los media types estan definidos en [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L15-L24).

### 3. Request

| Campo | Tipo | Obligatorio | Descripcion |
| --- | --- | --- | --- |
| `username` | string | Si | Nombre de usuario. Debe ser distinto de `null` y no estar vacio o contener solo espacios. El valor original se usa para consultar la base. |
| `password` | string | Si | Contrasena. Debe ser distinta de `null` y no estar vacia o contener solo espacios. El valor original se usa para consultar la base. |
| `client` | string | No | Campo aceptado por el DTO, pero no se usa en la autenticacion. |

Los campos y validaciones se observan en [LoginRequest.java](../src/main/java/com/bcp/workshop/auth/LoginRequest.java#L3-L7) y [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26-L35).

Ejemplo completo con las credenciales de laboratorio configuradas por el backend:

```json
{
  "username": "student",
  "password": "Workshop123!",
  "client": "angular-workshop"
}
```

### 4. Response exitosa

**Estado:** `200 OK`.

| Campo | Tipo | Descripcion |
| --- | --- | --- |
| `token` | string | Token Base64 generado para el usuario. Su contenido no es un JWT. |
| `expiresIn` | integer | Duracion anunciada en segundos; actualmente `3600`. |
| `user` | object | Datos del usuario autenticado. |
| `user.username` | string | Nombre de usuario. |
| `user.displayName` | string | Nombre visible, escapado con HTML4 antes de incluirlo en JSON. |
| `user.role` | string | Rol del usuario; las cuentas iniciales usan `STUDENT` o `TRAINER`. |
| `debug` | string | Indicador fijo con valor `authenticated-with-legacy-flow`. |

Ejemplo completo. El token es un valor de muestra generado por el algoritmo actual, no debe reutilizarse y expira; el servidor genera uno nuevo por respuesta.

```json
{
  "token": "c3R1ZGVudHxTVFVERU5UfDE3OTI0NDQ3NjF8V09SS1NIT1BfU0VDUkVUXzIwMjY=",
  "expiresIn": 3600,
  "user": {
    "username": "student",
    "displayName": "Workshop Student",
    "role": "STUDENT"
  },
  "debug": "authenticated-with-legacy-flow"
}
```

La respuesta se arma en [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L35-L50); las cuentas iniciales estan en [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L21-L24).

### 5. Respuestas de error

| Estado | Cuando ocurre | Ejemplo de cuerpo |
| --- | --- | --- |
| `400 Bad Request` | El objeto de solicitud es `null`. | `{"message":"Request is null"}` |
| `400 Bad Request` | `username` es `null`, vacio o solo contiene espacios. | `{"message":"Username is required"}` |
| `400 Bad Request` | `password` es `null`, vacia o solo contiene espacios. | `{"message":"Password is required"}` |
| `401 Unauthorized` | La consulta no encuentra una cuenta con ambas credenciales. | `{"message":"Invalid user unknown-user or password"}` |
| `500 Internal Server Error` | Una excepcion llega al `catch` del metodo. Los valores dependen de la excepcion; si no trae mensaje, `message` vale `Unknown error`. | `{"message":"<mensaje de la excepcion>","exception":"<nombre cualificado de la clase>"}` |

Los errores anteriores y sus cuerpos se construyen en [AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L26-L58). El repositorio captura por separado errores SQL y devuelve `null`, que el resource trata como credenciales invalidas: [LegacyDatabase.java](../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L32-L43).

**Pregunta abierta:** el resource no define el estado ni el cuerpo de errores ocurridos antes de invocar `login` (por ejemplo, JSON mal formado o un `Content-Type` no aceptado). Confirmar esos casos mediante una prueba de integracion antes de fijar su formato en este contrato.

### 6. Notas de seguridad

- El token actual es Base64 de `username|role|epochExpiry|secret`; Base64 no firma ni cifra ese contenido. El valor `expiresIn` y la marca de tiempo se generan al crear el token, pero este backend no contiene un verificador que haga cumplir la expiracion. Ver [LegacyTokenUtil.java](../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java#L8-L15).
- El token incluye el secreto hardcodeado `WORKSHOP_SECRET_2026`. Este es un backend de laboratorio; no usarlo ni las credenciales de ejemplo en produccion.
- La respuesta de error 500 devuelve el mensaje y la clase de la excepcion. No exponer esas respuestas a clientes no confiables.
- La configuracion actual permite CORS desde cualquier origen: [application.properties](../src/main/resources/application.properties#L4-L7).

## `GET /q/health`

### 1. Que hace

Permite a un operador o monitor determinar si la aplicacion informa estar disponible, agregando los health checks registrados por SmallRye Health.

### 2. Autenticacion

No se declara autenticacion para esta ruta y no requiere `Authorization`. Produce `application/json`. La ruta la publica `quarkus-smallrye-health`, declarada en [pom.xml](../pom.xml#L36-L43); no existe un recurso Java de health en este componente.

### 3. Request

| Campo | Tipo | Obligatorio | Descripcion |
| --- | --- | --- | --- |
| Ninguno | N/A | No | La operacion es `GET`; no define parametros de ruta, query ni cuerpo en el codigo del componente. |

No hay request JSON aplicable. Ejemplo de llamada sin cuerpo:

```http
GET /q/health
Accept: application/json
```

### 4. Response exitosa

**Estado:** `200 OK` cuando el estado agregado es `UP`.

| Campo | Tipo | Descripcion |
| --- | --- | --- |
| `status` | string | Estado agregado del health check. En el estado actual, `UP`. |
| `checks` | array de objetos | Resultados de health checks individuales. Este componente no registra health checks propios, por lo que el array esta vacio. |

Ejemplo completo para el estado actual, conforme al formato estandar de SmallRye Health:

```json
{
  "status": "UP",
  "checks": []
}
```

La extension expone el endpoint agregado `/q/health`; el formato `status`/`checks` y el array vacio cuando no hay checks estan descritos en la [guia oficial de SmallRye Health](https://quarkus.io/guides/smallrye-health). La dependencia esta declarada en [pom.xml](../pom.xml#L36-L43).

### 5. Respuestas de error

El codigo del componente no implementa respuestas de error propias para esta ruta. No hay checks de salud propios que puedan informar un estado `DOWN`.

**Pregunta abierta:** confirmar mediante una prueba de integracion el estado HTTP y el cuerpo que devuelve la version exacta de Quarkus incluida por el BOM si la extension informa `DOWN`, y el cuerpo de errores de ruta o transporte. No se fija aqui una respuesta que el codigo del componente no define.

### 6. Notas de seguridad

- La ruta no requiere autenticacion; cualquier cliente que alcance el servidor puede consultar el estado agregado.
- CORS esta habilitado y configurado para cualquier origen en [application.properties](../src/main/resources/application.properties#L4-L7). El endpoint actual solo publica el estado estandar, sin datos de checks propios.

## Documentos consultados

- `README.md`
- `REGLAS.md`
- `docs/GOOD_PRACTICES.md`
- `RETO.md`
- `pom.xml`
- Codigo de `AuthResource`, `LoginRequest`, `LegacyDatabase` y `LegacyTokenUtil`
- Guia oficial de Quarkus SmallRye Health para el formato estandar de `GET /q/health`