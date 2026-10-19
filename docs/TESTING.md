# Plan y resultados de pruebas

Este documento registra las pruebas de los endpoints descritos en `docs/ENDPOINTS.md`. No se inventan respuestas de error para `/q/health` ni para fallos de transporte que el contrato deja abiertos.

## Matriz

| endpoint | caso | tipo | resultado esperado |
| --- | --- | --- | --- |
| `POST /auth/login` | `logsInWithWorkshopUser` | Camino feliz | `200`; token no nulo; `expiresIn` es `3600`; `user` y `debug` conservan los campos y valores documentados. |
| `POST /auth/login` | `logsInWhenOptionalClientIsOmitted` | Caso borde | `200`; omitir `client` no impide autenticar. |
| `POST /auth/login` | `rejectsNullRequest` | Error `400` | Cuerpo `{"message":"Request is null"}`. |
| `POST /auth/login` | `rejectsMissingUsername` | Error `400` | Cuerpo `{"message":"Username is required"}`. |
| `POST /auth/login` | `rejectsWhitespaceUsername` | Caso borde / error `400` | Un usuario formado solo por espacios devuelve `{"message":"Username is required"}`. |
| `POST /auth/login` | `rejectsMissingPassword` | Error `400` | Cuerpo `{"message":"Password is required"}`. |
| `POST /auth/login` | `rejectsWhitespacePassword` | Caso borde / error `400` | Una contrasena formada solo por espacios devuelve `{"message":"Password is required"}`. |
| `POST /auth/login` | `rejectsInvalidCredentials` | Error `401` | Cuerpo `{"message":"Invalid user unknown-user or password"}`. |
| `POST /auth/login` | `returnsGenericShapeForUnexpectedException` | Error `500` | Estado `500`; el cuerpo contiene `message` y `exception` con los valores de la excepcion controlada. Esta prueba caracteriza una exposicion descrita como defecto abajo. |
| `GET /q/health` | `healthEndpointReportsUpWithNoRegisteredChecks` | Camino feliz | `200 application/json`; `status` es `UP` y `checks` es un arreglo vacio. |
| `GET /q/health` | `healthEndpointDoesNotRequireAuthorization` | Caso borde | Sin `Authorization` ni cuerpo de solicitud, devuelve `200` y `status: UP`. |

La suite no incluye pruebas de error para `GET /q/health`: `docs/ENDPOINTS.md` no documenta respuestas de error propias para esa ruta y el componente no registra health checks propios. Tampoco se afirman estados/cuerpos de errores de parsing, media type, ruta o transporte; estan pendientes de caracterizacion y de incorporacion al contrato.

## Codigo de pruebas

La implementacion mantenida por la suite esta en [`AuthResourceTest.java`](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java). Se replica a continuacion para facilitar la revision junto a la matriz.

```java
package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusMock;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .statusCode(200)
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("token", notNullValue())
                .body("expiresIn", is(3600))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void logsInWhenOptionalClientIsOmitted() {
        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .statusCode(200)
                .body("user.username", is("student"));
    }

    @Test
    void rejectsNullRequest() {
        postLogin("null")
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsMissingUsername() {
        postLogin("{\"password\":\"Workshop123!\"}")
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsWhitespaceUsername() {
        postLogin("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsMissingPassword() {
        postLogin("{\"username\":\"student\"}")
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsWhitespacePassword() {
        postLogin("{\"username\":\"student\",\"password\":\"   \"}")
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsInvalidCredentials() {
        postLogin("{\"username\":\"unknown-user\",\"password\":\"wrong\"}")
                .statusCode(401)
                .body("message", is("Invalid user unknown-user or password"));
    }

    @Test
    void returnsGenericShapeForUnexpectedException() {
        QuarkusMock.installMockForType(new LegacyDatabase() {
            @Override
            public UserRecord findByCredentials(String username, String password) {
                throw new IllegalStateException("controlled test failure");
            }
        }, LegacyDatabase.class);

        postLogin("{\"username\":\"student\",\"password\":\"Workshop123!\"}")
                .statusCode(500)
                .body("message", is("controlled test failure"))
                .body("exception", is(IllegalStateException.class.getName()));
    }

    @Test
    void healthEndpointReportsUpWithNoRegisteredChecks() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("status", is("UP"))
                .body("checks", empty());
    }

    @Test
    void healthEndpointDoesNotRequireAuthorization() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    private ValidatableResponse postLogin(String requestBody) {
        return given()
                .contentType("application/json")
                .body(requestBody)
                .when().post("/auth/login")
                .then();
    }
}
```

## Comandos y ejecuciones

**Directorio de ejecucion:** raiz del repositorio `workshop-auth-backend/`.

**Entorno verificado:** Windows PowerShell, Maven `3.10.0`, JDK `21.0.12`. El puerto `8081` estaba ocupado, por lo que se uso `8082` para Quarkus tests. En esta maquina, el JDK 21 se selecciono asi:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Ejecucion de la suite:

```powershell
mvn "-Dquarkus.http.test-port=8082" test
```

- Codigo de salida: `0`.
- Resultado: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`.

Ejecucion con generacion de cobertura:

```powershell
mvn "-Dquarkus.http.test-port=8082" verify
```

- Codigo de salida: `0`.
- Resultado: `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`.
- Medicion: JaCoCo `0.8.12`, fase `verify`, reporte HTML en `target/site/jacoco/index.html` y datos CSV en `target/site/jacoco/jacoco.csv`.
- Resultado agregado de las cinco clases productivas: cobertura de lineas `94.12%` (`48/51`) e instrucciones `96.89%`.
- Clase con menor cobertura de lineas: `LegacyDatabase`, `85.71%` (`18/21`). `AuthResource` tiene `100%` (`25/25`).
- Metodo: suma de `LINE_COVERED / (LINE_COVERED + LINE_MISSED)` e `INSTRUCTION_COVERED / (INSTRUCTION_COVERED + INSTRUCTION_MISSED)` de las filas de clases en `jacoco.csv`. JaCoCo mide clases productivas, no las clases de prueba. El umbral de lineas supera el `85%` de `REGLAS.md` para el codigo productivo medido.

## Fallos

No quedan fallos de pruebas en la ultima ejecucion. El primer intento de validacion de las pruebas nuevas no compilo porque una llamada de test tenia dos argumentos; se corrigio y tanto `test` como `verify` terminaron con codigo `0`.

## Defectos observados aparte

La prueba `returnsGenericShapeForUnexpectedException` caracteriza el contrato actual; no corrige ni normaliza la filtracion. `AuthResource` incluye al cliente el mensaje y la clase de la excepcion en el `500` ([AuthResource.java](../src/main/java/com/bcp/workshop/auth/AuthResource.java#L54-L58)), contrario a la recomendacion de errores genericos de `docs/GOOD_PRACTICES.md`. Debe tratarse como defecto de seguridad y no como comportamiento deseable.

## Pendientes

- Obtener mediante prueba de integracion los estados/cuerpos de errores previos a `login` (JSON mal formado y media type no aceptado) y actualizar `docs/ENDPOINTS.md` antes de fijar expectativas automatizadas.
- Si se agrega un health check o se decide documentar el estado `DOWN`, definir primero su contrato y luego probar estado HTTP y cuerpo. Actualmente no hay respuesta de error propia documentada para health.

## Pruebas propuestas

- Caracterizar el parsing de JSON mal formado y `Content-Type` no aceptado en la version exacta de Quarkus, despues de acordar/documentar sus respuestas.
- Cuando exista un health check que pueda fallar, agregar el escenario `DOWN` con el estado/cuerpo especificado por el contrato.
