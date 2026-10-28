# FastFood Backend

Backend de comida rapida con microservicios Spring Boot: autenticacion y usuarios, catalogo, pedidos, comprobantes SOAP y comunicacion WebSocket. Eureka registra los servicios y el gateway centraliza las rutas REST.

## Requisitos

- JDK 17 y MySQL disponible en `localhost:3306`.
- IntelliJ IDEA o Spring Tool Suite con soporte Maven, o una terminal. Cada servicio incluye Maven Wrapper.
- El usuario de MySQL debe poder crear las bases y tablas. Las conexiones incluyen `createDatabaseIfNotExist=true` y Hibernate utiliza `ddl-auto=update` para desarrollo local.

## Configuracion

Clona el repositorio e importa las siete carpetas de servicios como proyectos Maven. En la configuracion de ejecucion de cada servicio, define las variables de entorno siguientes (IntelliJ: Run > Edit Configurations > Environment variables; STS: Run Configurations > Environment).

| Servicio | Puerto | Variables obligatorias |
|---|---|---|
| `eureka-server` | 8761 | Ninguna |
| `auth-service` | 8081 | `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `INTERNAL_SECRET` |
| `catalog-service` | 8082 | `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `INTERNAL_SECRET` |
| `order-service` | 8083 | `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `INTERNAL_SECRET` |
| `comprobante-soap-service` | 8084 | `DB_USERNAME`, `DB_PASSWORD`, `INTERNAL_SECRET` |
| `socket-server` | 8085; WebSocket 8887 | `JWT_SECRET`, `INTERNAL_SECRET` |
| `api-gateway` | 8080 | `JWT_SECRET` |

`.env.example` es solo una guia: no se carga automaticamente y no necesitas crear un `.env`. Configura las credenciales de tu MySQL en cada servicio que lo utilice. Las bases son `fastfood_auth_db`, `fastfood_catalog_db`, `fastfood_order_db` y `fastfood_comprobante_db`.

Genera dos claves aleatorias distintas para `JWT_SECRET` e `INTERNAL_SECRET`. Cada clave debe tener al menos 32 bytes; puedes generar 32 bytes aleatorios y usar su representacion hexadecimal de 64 caracteres. Usa el mismo `JWT_SECRET` en todos los servicios que lo requieren y el mismo `INTERNAL_SECRET` en los que lo requieren. Cada instalacion debe generar sus propias claves; nunca las subas al repositorio ni las compartas con el frontend.

Para generar una clave en PowerShell (ejecuta dos veces y guarda cada resultado en la variable correspondiente del IDE):

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
```

Opcionalmente, en Auth activa `DEMO_ENABLED=true` y configura `DEMO_ADMIN_PASSWORD` para crear el administrador demo si no existe. `DEMO_ADMIN_EMAIL` permite cambiar el correo predeterminado `admin@fastfood.local`. Esta opcion no cambia la contrasena de una cuenta existente.

## Ejecucion

1. Inicia MySQL y luego `eureka-server`.
2. Inicia `auth-service`, `catalog-service`, `comprobante-soap-service`, `socket-server` y `order-service`.
3. Inicia `api-gateway` y espera a que todos aparezcan en Eureka: <http://localhost:8761>.

La entrada REST es `http://localhost:8080/api`. El frontend permitido por defecto es `http://localhost:5173`.

Desde terminal, define primero las variables de entorno y ejecuta `./mvnw.cmd spring-boot:run` dentro de cada servicio (en Linux/macOS: `./mvnw spring-boot:run`).

## Pruebas

Ejecuta `./mvnw.cmd test` dentro de cada servicio. Las pruebas cargan claves publicas exclusivas de prueba desde `src/test/resources/secrets-test.properties`; no necesitan tus claves JWT reales. Las pruebas con MySQL requieren `DB_USERNAME` y `DB_PASSWORD` y crean bases separadas con sufijo `_test_db`.

Para usar `soapui/FastFood-soapui-project.xml`, configura la propiedad de proyecto `ClaveTecnica` con el mismo valor de `INTERNAL_SECRET` de tu instalacion. Evita guardar ese valor en el archivo que subas a GitHub.
