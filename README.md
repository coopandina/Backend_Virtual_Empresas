# apiVirtualEmpresa (Virtual Empresas Backend)

Virtual Empresas es el backend principal de la plataforma, desarrollado por Ing. Oscar Moreira. Está construido utilizando **Spring Boot 3.3.6** y **Java 23**, enfocado en proporcionar servicios empresariales seguros y eficientes, incluyendo manejo de nóminas, transferencias bancarias, firmas electrónicas (SRI) y seguridad con JWT.

## Características Principales

*   **Autenticación y Seguridad:** Integración de Spring Security y `io.jsonwebtoken` para el control de acceso y manejo de sesiones con JWT.
*   **Gestión de Base de Datos:** Soporte para bases de datos relacionales utilizando **Spring Data JPA**. Incluye dependencias para:
    *   **MySQL** (`mysql-connector-j`)
    *   **Informix** (`com.ibm.informix.jdbc`)
    *   **HikariCP** como pool de conexiones.
*   **Manejo de Transacciones Financieras:** Procesamiento de transferencias directas e interbancarias, nóminas y control de saldos.
*   **Firmas Electrónicas y Facturación Electrónica (SRI):** Módulos completos para manejo de XAdES, BouncyCastle y las dependencias de MITyC y SRI para Ecuador.
*   **Generación de Documentos:** Creación de documentos utilizando **Apache PDFBox** y generación de códigos QR mediante **ZXing**.
*   **Notificaciones:** Integración con librerías internas para el envío de **SMS** y **Correos Electrónicos** (`libSMS`, `libEmailSms`).

## Tecnologías Utilizadas

*   **Java:** 23
*   **Framework Principal:** Spring Boot 3.3.6
*   **ORM:** Hibernate / Spring Data JPA
*   **Seguridad:** Spring Security, JWT (JSON Web Tokens)
*   **Bases de Datos:** Informix, MySQL
*   **Utilidades:** Lombok, org.json, Apache Commons (Codec, HttpClient, Lang)
*   **Construcción:** Maven

## Dependencias Internas

El proyecto hace uso de varias bibliotecas desarrolladas internamente (`groupId: ApiVirtualEmpresas`):
*   `libsVirtual` (v1.0.0)
*   `libSMS` (v3.0.0)
*   `libEmailSms` (v12.0.0)
*   `libClavesCore` (v3.0.0)

*(Es necesario tener instaladas estas dependencias en tu repositorio Maven local o en el servidor de Nexus/Artifactory de la empresa antes de compilar el proyecto).*

## Requisitos Previos

1.  **Java Development Kit (JDK) 23** instalado en tu sistema.
2.  **Apache Maven** configurado en el PATH.
3.  Acceso a las bases de datos (Informix/MySQL) configuradas en el entorno.
4.  Librerías del **SRI y MITyC** instaladas en tu repositorio local de Maven, así como las dependencias internas descritas anteriormente.

## Construcción y Ejecución

Para compilar y empaquetar la aplicación, abre una terminal en la raíz del proyecto y ejecuta:

```bash
mvn clean install
```

Para ejecutar el servidor localmente en modo desarrollo:

```bash
mvn spring-boot:run
```

O bien, ejecuta directamente el archivo JAR generado:

```bash
java -jar target/apiVirtualEmpresa.jar
```

## Estructura del Proyecto

La estructura principal del código sigue las convenciones de Spring Boot. Algunas áreas clave incluyen:
*   `apiVirtualEmpresa.apiVirtualEmpresa.nominas`: Módulos para procesamiento de nóminas internas y externas.
*   `apiVirtualEmpresa.apiVirtualEmpresa.config`: Configuraciones globales de Spring (Seguridad, Interceptores, Manejo de Excepciones).
*   `apiVirtualEmpresa.apiVirtualEmpresa.login`: Módulos para autenticación, expiración de tokens, etc.

## Autores y Mantenimiento

*   **Desarrollo original:** Ing. Oscar Moreira
*   **Mantenimiento actual:** Pasante Fernando Guanoluisa

---
*README generado automáticamente según la configuración del archivo pom.xml*
