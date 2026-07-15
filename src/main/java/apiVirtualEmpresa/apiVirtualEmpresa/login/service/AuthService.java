package apiVirtualEmpresa.apiVirtualEmpresa.login.service;

import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import apiVirtualEmpresa.apiVirtualEmpresa.login.dto.CodSegurdiad;
import apiVirtualEmpresa.apiVirtualEmpresa.login.dto.UserCredentials;
import apiVirtualEmpresas.virtualempresas.libs.Libs;
import envioCorreo.sendEmail;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import libs.PassSecure;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sms.SendSMS;

import java.util.*;

@Transactional
@Service
@RequiredArgsConstructor

public class AuthService {

    private final JwtUtil jwtUtil;

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private TokenExpirationService tokenExpirationService;

    private int intentosRealizados = 0, intentosRealizadoTokenFallos = 0;

    public ResponseEntity<Map<String, Object>> accesslogin(UserCredentials request, HttpServletResponse responseserve) {
        try {
            Map<String, Object> allData = new HashMap<>();
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> allDataList = new ArrayList<>();
            HttpStatus status = HttpStatus.OK;

            String mensajeValBlancos = validarCredencialesBlanco(request);
            if (mensajeValBlancos != null) {
                allData.put("message", mensajeValBlancos);
                allData.put("status", "AA01");
                allData.put("errors", "No se puede enviar campos con espacios en blanco. ");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            String mensajeUsarioNoSerCorreo = usarioNoSerCorreo(request);
            if (mensajeUsarioNoSerCorreo != null) {
                allData.put("message", mensajeUsarioNoSerCorreo);
                allData.put("status", "AA02");
                allData.put("errors", "No se acepta correos electronicos en el usuario. ");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String accesoDipTermi = request.getIpterminal();

            if (request.getClienIdeClien() != null && request.getClienIdeClien().contains("ñ")) {
                allData.put("message", "Usuario inválido");
                allData.put("status", "AA03");
                allData.put("errors", "No se permite el carácter 'ñ' en el usuario");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            if (request.getUsvcoPswd() != null && request.getUsvcoPswd().contains("ñ")) {
                allData.put("message", "Contraseña inválido");
                allData.put("status", "AA04");
                allData.put("errors", "No se permite el carácter 'ñ' en la password");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            List<Object[]> resultados = valida_usuario_id(request.getClienIdeClien(), request.getUsvcoIdeUsv());

            if (!resultados.isEmpty()) {

                String rucIdenClie = request.getClienIdeClien();
                String ideClieUsu = request.getUsvcoIdeUsv();
                String pwsUsoClie = request.getUsvcoPswd();
                Map<String, Object> validacion = valida_LoginBDD(rucIdenClie, ideClieUsu, pwsUsoClie, accesoDipTermi);
                if (Boolean.TRUE.equals(validacion.get("success"))) {
                    String cod_cliente = (String) validacion.get("CODCLIENTE");

                    allData.put("message", "Acceso concedido.");
                    allData.put("status", "AA3684");

                    // [kguanoluisa] - Generar SessionId único e insertar registro en pendiente (0)
                    // - 12/05/2026
                    String sessionId = java.util.UUID.randomUUID().toString();

                    String sqlRegSession = "INSERT INTO andctrlvirlogin (ctrlvirlogin_ide_virtual, ctrlvirlogin_user_virtual, "
                            +
                            "ctrlvirlogin_mac_virtual, ctrlvirlogin_cod_temporal, ctrlvirlogin_ip_login, " +
                            "ctrlvirlogin_fecha_virtual, ctrlvirlogin_ctrl_virtual) " +
                            "VALUES (:ide, :user, ' ', :uuid, :ip, CURRENT, 0)";
                    Query querySession = entityManager.createNativeQuery(sqlRegSession);
                    querySession.setParameter("ide", request.getClienIdeClien());
                    querySession.setParameter("user", request.getUsvcoIdeUsv());
                    querySession.setParameter("uuid", sessionId);
                    querySession.setParameter("ip", accesoDipTermi);
                    querySession.executeUpdate();

                    // 1) Token generado incluyendo el sessionId
                    String token = jwtUtil.generateToken(request.getUsvcoIdeUsv(), request.getClienIdeClien(),
                            cod_cliente, sessionId);

                    // 2) Crear Cookie HttpOnly (modo local)
                    Cookie cookie = new Cookie("jwt", token);
                    cookie.setHttpOnly(true);
                    cookie.setSecure(false); // false en desarrollo
                    cookie.setPath("/");
                    cookie.setMaxAge(24 * 60 * 60);
                    responseserve.addCookie(cookie);

                    // 3) Convertir la cookie a string manual (más control)
                    String cookieString = String.format(
                            "jwt=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                            token, 24 * 60 * 60);

                    // 4) (Opcional) agregar la cookie a la respuesta
                    responseserve.addCookie(cookie);

                    status = HttpStatus.OK;

                    // 5) Armar body normal
                    allDataList.add(allData);
                    response.put("AllData", allDataList);

                    // 6) →→ Aquí SOLO ENVÍAS UNA COOKIE ←←

                    return new ResponseEntity<>(response, status);

                } else if (Boolean.TRUE.equals(validacion.get("cod4digitos"))) {
                    allData.put("message", "Debe cambiar su contraseña o es una contraseña temporal.");
                    allData.put("status", "AA06");
                    allData.put("errors", "Usuario con contraseña temporal.");
                    allData.put("token", validacion.get("token"));
                    status = HttpStatus.BAD_REQUEST;
                } else {
                    allData.put("message", validacion.get("message"));
                    allData.put("status", validacion.get("status"));
                    allData.put("errors", validacion.get("errors"));
                    status = HttpStatus.BAD_REQUEST;
                }
                allDataList.add(allData);
                response.put("AllData", allDataList);
            } else {
                allData.put("message",
                        "Por favor, revise que la informacion ingresada sea la correcta, ruc o identificacion ");
                allData.put("status", "AA3684");
                allData.put("errors", "Credenciales incorrectas");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                status = HttpStatus.BAD_REQUEST;
            }
            return new ResponseEntity<>(response, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // [kguanoluisa] - Cierre lógico de sesión en base de datos y expiración de
    // cookies - 12/05/2026
    @Transactional
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request, HttpServletResponse responseServe) {
        Map<String, Object> allData = new HashMap<>();
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {
            String token = Obtenertoken.desdeCookie(request);
            if (token != null) {
                String sessionId = jwtUtil.getSessionIdFromToken(token);
                if (sessionId != null) {
                    String sqlLogout = "UPDATE andctrlvirlogin SET ctrlvirlogin_ctrl_virtual = 0 WHERE ctrlvirlogin_cod_temporal = :uuid";
                    Query qLog = entityManager.createNativeQuery(sqlLogout);
                    qLog.setParameter("uuid", sessionId);
                    qLog.executeUpdate();
                }
            }
        } catch (Exception e) {
            // kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback del
            // UPDATE][][2026-05-21]
            throw new RuntimeException("Error en logout al actualizar estado: " + e.getMessage(), e);
        }

        Cookie cookie = new Cookie("jwt", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        responseServe.addCookie(cookie);

        allData.put("message", "Sesión cerrada con éxito.");
        allData.put("status", "LO00");
        allDataList.add(allData);
        response.put("AllData", allDataList);
        response.put("success", true);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public String validarCredencialesBlanco(UserCredentials request) {
        if (request.getClienIdeClien() == null || request.getClienIdeClien().isEmpty()) {

            return "El RUC ingresado no puede estar en blanco o nulo";
        }

        if (request.getUsvcoIdeUsv() == null || request.getUsvcoIdeUsv().isEmpty()) {
            return "La cedula del usuario no puede ser null o estar en blanco";
        }
        if (request.getUsvcoPswd() == null || request.getUsvcoPswd().isEmpty()) {
            return "La clave personal no puede ser null o estar en blanco";
        }
        return null;
    }

    public String usarioNoSerCorreo(UserCredentials request) {
        String regexCorreo = "^[\\w-]+(?:\\.[\\w-]+)*@[\\w-]+(?:\\.[\\w-]+)+$";
        String ideruc = request.getClienIdeClien();
        String ideclien = request.getUsvcoIdeUsv();

        if (ideruc.matches(regexCorreo)) {
            return "El ruc del cliente no puede ser un correo electrónico.";
        }
        if (ideclien.matches(regexCorreo)) {
            return "El ruc del cliente no puede ser un correo electrónico.";
        }
        return null;
    }

    public List<Object[]> valida_usuario_id(String ideruc, String ideusu) {

        String sql = "SELECT clien_ide_clien,usvco_psw_usvco,clien_cod_ofici,usvco_ctr_estad,usvco_ctr_bloq " +
                "FROM andusvco, cnxclien " +
                "WHERE clien_ide_clien= :ideruc " +
                "AND usvco_ide_usvco= :ideusu " +
                "AND usvco_ide_clien=clien_ide_clien";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("ideruc", ideruc);
        query.setParameter("ideusu", ideusu);
        List<Object[]> results = query.getResultList();

        return results;
    }

    /**
     * Funcion para validar Login con LA BDD
     */

    public Map<String, Object> valida_LoginBDD(String rudIdenClie, String ideClieUsu, String pwsUsoClie,
            String accesoDipTermi) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Consulta para verificar usuario y contraseña
            String sql = "SELECT clien_ide_clien,usvco_ide_usvco, usvco_psw_usvco " +
                    "FROM andusvco, cnxclien " +
                    "WHERE clien_ide_clien=:rudIdenClie " +
                    "AND usvco_ide_usvco=:ideClieUsu " +
                    "AND usvco_ide_clien=clien_ide_clien";
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("rudIdenClie", rudIdenClie);
            query.setParameter("ideClieUsu", ideClieUsu);
            List<Object[]> results = query.getResultList();
            if (results.isEmpty()) {
                response.put("success", false);
                response.put("message", "El ruc ingresado o la identificacion  no es incorrecto, intente nuevamente.");
                response.put("status", "AA3410");
                response.put("errors", "Usuario no encontrado.");
                return response;
            }
            // Consulta para verificar si el usuario está bloqueado
            String sqlBloq = "SELECT usvco_ctr_bloq,usvco_ctr_estad FROM andusvco WHERE usvco_ide_clien=:rudIdenClie " +
                    "AND usvco_ide_usvco=:ideClieUsu";
            Query resultSQLBloq = entityManager.createNativeQuery(sqlBloq);
            resultSQLBloq.setParameter("rudIdenClie", rudIdenClie);
            resultSQLBloq.setParameter("ideClieUsu", ideClieUsu);
            List<Object[]> results1 = resultSQLBloq.getResultList();
            // Validación de las credenciales
            for (Object[] row : results) {
                String cliacUsuVirtu = (String) row[0];
                String clienIdeVirtu = (String) row[1];
                String clienWwwPswrd = (String) row[2];
                for (Object[] row1 : results1) {
                    String cliacBloq = row1[0].toString();
                    String clien_estado = row1[1].toString();

                    // Limpieza de datos
                    clienWwwPswrd = clienWwwPswrd.trim();

                    PassSecure passSecure = new PassSecure();
                    clienWwwPswrd = clienWwwPswrd.replace("\"", "");
                    String passDec = null;
                    try {
                        // [kguanoluisa] - Capturar errores de codificación/desencriptación de
                        // contraseña (evitar caídas por passwords corruptos) - 12/05/2026
                        passDec = passSecure.decryptPassword(clienWwwPswrd);
                    } catch (Exception ex) {
                        response.put("success", false);
                        response.put("message",
                                "Error en la codificación o desencriptación de la contraseña almacenada.");
                        response.put("status", "AAERRCODIF");
                        // response.put("errors", "Fallo de descifrado interno: " + ex.getMessage());
                        return response;
                    }

                    if (passDec != null) {
                        passDec = passDec.trim().replace("\"", ""); // elimina comillas dobles
                    }

                    String sqlDatosInfoToken = "SELECT usvco_ide_usvco, clien_cod_clien " +
                            "FROM andusvco, cnxclien " +
                            "WHERE clien_ide_clien=:rudIdenClie " +
                            "AND usvco_ide_usvco=:ideClieUsu " +
                            "AND usvco_ide_clien=clien_ide_clien";
                    Query resulDatosInfoToken = entityManager.createNativeQuery(sqlDatosInfoToken);
                    resulDatosInfoToken.setParameter("rudIdenClie", rudIdenClie);
                    resulDatosInfoToken.setParameter("ideClieUsu", ideClieUsu);
                    List<Object[]> results3 = resulDatosInfoToken.getResultList();
                    // Verificar si hay resultados
                    if (results3.isEmpty()) {
                        response.put("success", false);
                        response.put("message", "No se encontraron datos para el usuario");
                        return response;
                    }
                    // Procesar el primer resultado
                    Object[] row10 = results3.get(0);
                    String clienCedula1 = row10[0].toString().trim();
                    String clienCodClie1 = row10[1].toString().trim();

                    if (cliacUsuVirtu.trim().equals(rudIdenClie.trim()) &&
                            passDec.trim().equals(pwsUsoClie.trim())
                            && ideClieUsu.trim().equals(clienIdeVirtu.trim())) {

                        intentosRealizados = 0;
                        if (!results1.isEmpty()) {
                            if ("0".equals(cliacBloq.trim()) || "0".equals(clien_estado.trim())) {
                                response.put("success", false);
                                response.put("message", "El usuario está bloqueado o no está activo.");
                                response.put("status", "AA1812");
                                response.put("errors", "Usuario bloqueado.");
                                return response;
                            } else {
                                // [kguanoluisa] - Bloquear si ya existe una sesión activa hace menos de 15 min
                                // - 12/05/2026
                                String sqlCheckSesion = "SELECT COUNT(*) FROM andctrlvirlogin " +
                                        "WHERE ctrlvirlogin_ide_virtual = :ide " +
                                        "AND ctrlvirlogin_user_virtual = :user " +
                                        "AND ctrlvirlogin_ctrl_virtual = 1 " +
                                        "AND ctrlvirlogin_fecha_virtual > CURRENT - INTERVAL(15) MINUTE TO MINUTE";
                                Query qCheck = entityManager.createNativeQuery(sqlCheckSesion);
                                qCheck.setParameter("ide", rudIdenClie);
                                qCheck.setParameter("user", ideClieUsu);
                                Number active = (Number) qCheck.getSingleResult();

                                if (active.intValue() > 0) {
                                    response.put("success", false);
                                    response.put("message",
                                            "Ya cuenta con una sesión activa en otra ventana o dispositivo.");
                                    response.put("status", "AASESIONACTIVA");
                                    response.put("errors", "Sesión simultánea denegada.");
                                    return response;
                                }

                                String sqlDatosCorreo = "select clien_ape_clien,clien_nom_clien ,usvco_ema_usvco, " +
                                        "usvco_tlf_usvco, usvco_ide_usvco,clien_cod_clien  from cnxclien, andusvco where clien_ide_clien=:rudIdenClie "
                                        +
                                        "and usvco_ide_usvco=:ideClieUsu ";
                                Query resulDatosCorreo = entityManager.createNativeQuery(sqlDatosCorreo);
                                resulDatosCorreo.setParameter("rudIdenClie", rudIdenClie);
                                resulDatosCorreo.setParameter("ideClieUsu", ideClieUsu);

                                List<Object[]> results2 = resulDatosCorreo.getResultList();
                                for (Object[] row2 : results2) {
                                    String clienApellidos = row2[0].toString().trim();
                                    String clienNombres = row2[1].toString().trim();
                                    String clienEmail = row2[2].toString().trim();
                                    String clienNumero = row2[3].toString().trim();
                                    String clienCedula = row2[4].toString().trim();
                                    String clienCodClie = row2[5].toString().trim();
                                    // System.out.println("Consulta BDD= APELLIDOS: " + clienApellidos + " NOMBRES:
                                    // " + clienNombres + " EMAIL: " + clienEmail + " CELULAR " + clienNumero);
                                    Libs fechaHoraService = new Libs(entityManager);

                                    String FechaIngresoLogin = fechaHoraService.obtenerFechaYHora();
                                    // System.out.println(FechaIngresoLogin);

                                    String tokenTemp = codigoAleatorioTemp();

                                    SendSMS smsCodigoTemp = new SendSMS();
                                    smsCodigoTemp.sendSecurityCodeSMS(clienNumero, "1150", tokenTemp, "Iniciar Sesion",
                                            FechaIngresoLogin);
                                    sendEmail enviaCorreoToken = new sendEmail();
                                    tokenExpirationService.programarExpiracionToken(rudIdenClie, tokenTemp, "8");
                                    enviaCorreoToken.sendEmailTokenTemp(clienApellidos, clienNombres, FechaIngresoLogin,
                                            clienEmail, tokenTemp);
                                    String sqlUpdateEstado = "UPDATE vircodaccess SET codaccess_estado = '0' WHERE codaccess_usuario = :codaccess_cedula AND codaccess_estado = '1' and  codsms_codigo = '8' ";

                                    Query resultUpdateEstado = entityManager.createNativeQuery(sqlUpdateEstado);
                                    resultUpdateEstado.setParameter("codaccess_cedula", clienCedula); // o
                                                                                                      // cliacUsuVirtu,
                                                                                                      // dependiendo de
                                                                                                      // qué campo estés
                                                                                                      // usando para
                                                                                                      // identificar al
                                                                                                      // usuario
                                    resultUpdateEstado.executeUpdate();

                                    String sqlInsertToken = "INSERT INTO vircodaccess (codaccess_cedula, codaccess_usuario, codaccess_codigo_temporal, codsms_codigo, codaccess_estado, codaccess_fecha) VALUES (:codaccess_cedula, :codaccess_usuario, :codaccess_codigo_temporal, :codsms_codigo, :codaccess_estado, :codaccess_fecha)";
                                    Query resultInsertTokenAcceso = entityManager.createNativeQuery(sqlInsertToken);
                                    resultInsertTokenAcceso.setParameter("codaccess_cedula", cliacUsuVirtu);
                                    resultInsertTokenAcceso.setParameter("codaccess_usuario", clienCedula);
                                    resultInsertTokenAcceso.setParameter("codaccess_codigo_temporal", tokenTemp);
                                    resultInsertTokenAcceso.setParameter("codsms_codigo", 8);
                                    resultInsertTokenAcceso.setParameter("codaccess_estado", "1");
                                    resultInsertTokenAcceso.setParameter("codaccess_fecha", FechaIngresoLogin);
                                    resultInsertTokenAcceso.executeUpdate();
                                    tokenExpirationService.programarExpiracionToken(clienCedula, tokenTemp, "8");

                                    String accesoMacTermi = " ";
                                    Libs fechaHoraServicee = new Libs(entityManager);
                                    String accesoFecAcces = fechaHoraServicee.obtenerFechaYHora();
                                    String accesoCodAcces = generarNumberoSerial(1000000, 9999999);
                                    String accesoDesUsuar = cliacUsuVirtu;
                                    String accesoPasUsuar = clienWwwPswrd;
                                    String accesoCodTacce = "1";

                                    String sqlInsertAccesos = "INSERT INTO andacceso VALUES (:acceso_cod_acces, :acceso_des_usuar, :acceso_pas_usuar, :acceso_fec_acces, :acceso_dip_termi, :acceso_mac_termi, :acceso_cod_tacce)";
                                    Query resultInsertAcceso = entityManager.createNativeQuery(sqlInsertAccesos);
                                    resultInsertAcceso.setParameter("acceso_cod_acces", accesoCodAcces);
                                    resultInsertAcceso.setParameter("acceso_des_usuar", accesoDesUsuar);
                                    resultInsertAcceso.setParameter("acceso_pas_usuar", accesoPasUsuar);
                                    resultInsertAcceso.setParameter("acceso_fec_acces", accesoFecAcces);
                                    resultInsertAcceso.setParameter("acceso_dip_termi", accesoDipTermi);
                                    resultInsertAcceso.setParameter("acceso_mac_termi", accesoMacTermi);
                                    resultInsertAcceso.setParameter("acceso_cod_tacce", accesoCodTacce);
                                    resultInsertAcceso.executeUpdate();

                                    // String token = JwtUtil.generateToken(cliacUsuVirtu, clienCedula,
                                    // clienCodClie);
                                    response.put("success", true);
                                    response.put("message", "Inicio de sesión exitoso.");
                                    response.put("CODCLIENTE", clienCodClie);
                                }
                            }
                        }
                        response.put("success", true);
                        response.put("message", "Inicio de sesión exitoso.");
                        return response;
                    } else {
                        intentosRealizados++;

                        if (intentosRealizados >= 3) {
                            String sqlBloqUser = "UPDATE andusvco SET usvco_ctr_bloq = :bloqueo WHERE usvco_ide_clien = :rudIdenClie AND usvco_ide_usvco = :ideClieUsu";
                            Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                            resultBloqUser.setParameter("bloqueo", "0");
                            resultBloqUser.setParameter("rudIdenClie", rudIdenClie);
                            resultBloqUser.setParameter("ideClieUsu", ideClieUsu);
                            // MANDAR CORREO DE BLOQUEO
                            try {
                                // Ejecutar la actualización
                                int rowsUpdated = resultBloqUser.executeUpdate();
                                if (rowsUpdated > 0) {

                                    String accesoMacTermi = " ";
                                    Libs fechaHoraService = new Libs(entityManager);
                                    String accesoFecAcces = fechaHoraService.obtenerFechaYHora();
                                    String accesoCodAcces = generarNumberoSerial(100000, 999999);
                                    String accesoDesUsuar = cliacUsuVirtu;
                                    String accesoPasUsuar = clienWwwPswrd;
                                    String accesoCodTacce = "2";
                                    System.out.println(accesoCodAcces);
                                    String sqlInsertAccesos = "INSERT INTO andacceso VALUES (:acceso_cod_acces, :acceso_des_usuar, :acceso_pas_usuar, :acceso_fec_acces, :acceso_dip_termi, :acceso_mac_termi, :acceso_cod_tacce)";
                                    Query resultInsertAcceso = entityManager.createNativeQuery(sqlInsertAccesos);
                                    resultInsertAcceso.setParameter("acceso_cod_acces", accesoCodAcces);
                                    resultInsertAcceso.setParameter("acceso_des_usuar", accesoDesUsuar);
                                    resultInsertAcceso.setParameter("acceso_pas_usuar", accesoPasUsuar);
                                    resultInsertAcceso.setParameter("acceso_fec_acces", accesoFecAcces);
                                    resultInsertAcceso.setParameter("acceso_dip_termi", accesoDipTermi);
                                    resultInsertAcceso.setParameter("acceso_mac_termi", accesoMacTermi);
                                    resultInsertAcceso.setParameter("acceso_cod_tacce", accesoCodTacce);
                                    resultInsertAcceso.executeUpdate();
                                    intentosRealizadoTokenFallos = 0;
                                    System.out.println("Usuario bloqueado exitosamente en la base de datos.");
                                    intentosRealizados = 0;
                                } else {
                                    System.out.println("No se encontró al usuario para bloquear.");
                                }
                            } catch (Exception e) {
                                // kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback del
                                // UPDATE de bloqueo][][2026-05-21]
                                throw new RuntimeException(
                                        "Error al intentar bloquear el usuario en la base de datos: " + e.getMessage(),
                                        e);
                            }
                            response.put("success", false);
                            response.put("message", "Se alcanzó el límite de intentos.");
                            response.put("status", "AA08");
                            response.put("errors", "Usuario bloqueado por demasiados intentos fallidos.");
                            return response;
                        }
                    }
                }
            }
            response.put("success", false);
            response.put("message", "Credenciales incorrectas.");
            response.put("status", "AA12");
            response.put("errors", "Contraseña incorrecta o usuario no encontrado.");
            return response;

        } catch (Exception e) {
            // [kguanoluisa] - Propagar la excepción original para evitar enmascaramiento
            // por UnexpectedRollbackException y capturarla en GlobalExceptionHandler -
            // 18/05/2026
            throw new RuntimeException(e);
        }
    }

    public ResponseEntity<Map<String, Object>> validarCodSeguridad(HttpServletRequest request,
            CodSegurdiad codSeguridad, Authentication authentication) {
        try {
            Map<String, Object> allData = new HashMap<>();
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> allDataList = new ArrayList<>();
            HttpStatus status = HttpStatus.OK;

            String token = Obtenertoken.desdeCookie(request);
            if (token == null) {
                response.put("success", false);
                allData.put("status", "AA027");
                allData.put("errors", "No autorizado: no fue posible generar el token de acceso.");

                response.put("AllData", allDataList);

                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }
            // 2. VALIDAR AUTENTICACIÓN SPRING

            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "No autorizado");
                allData.put("status", "AA028");
                allData.put("errors", "La sesión no es válida o ha expirado.");
                response.put("AllData", allDataList);

                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String cliacUsuVirtu = authentication.getName();
            String rucIdenClie = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (cliacUsuVirtu == null || rucIdenClie == null || numSocio == null) {
                allData.put("message", "Datos del token incompletos");
                allData.put("status", "AA022");
                allData.put("errors", "ERROR EN LA AUTENTICACIÓN");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (codSeguridad.getCodaccess_codigo_temporal() == null
                    || !codSeguridad.getCodaccess_codigo_temporal().matches("\\d{6}")) {
                allData.put("message", "Código de seguridad inválido");
                allData.put("status", "AA023");
                allData.put("errors", "El código debe contener exactamente 6 dígitos");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String mensajeValidarCodigoSeguridad = validarCodigoSeguridad(codSeguridad);
            if (mensajeValidarCodigoSeguridad != null) {
                allData.put("message", mensajeValidarCodigoSeguridad);
                allData.put("status", "AA021");
                allData.put("errors", "ERROR EN EL CÓDIGO DE SEGURIDAD");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String sqlVerificaTokenBDD = "SELECT codaccess_codigo_temporal FROM vircodaccess " +
                    "WHERE codaccess_cedula = :codaccess_cedula AND codaccess_usuario = :codaccess_usuario AND codaccess_estado = :codaccess_estado "
                    +
                    "AND codsms_codigo = '8'";

            Query queryVerificaTokenBDD = entityManager.createNativeQuery(sqlVerificaTokenBDD);
            queryVerificaTokenBDD.setParameter("codaccess_cedula", rucIdenClie);
            queryVerificaTokenBDD.setParameter("codaccess_usuario", cliacUsuVirtu);
            queryVerificaTokenBDD.setParameter("codaccess_estado", "1");
            List<Object[]> resultsTokenBDD = queryVerificaTokenBDD.getResultList();

            /// verifica sms datos
            if (!resultsTokenBDD.isEmpty()) {
                String tokenFromDB = (String) queryVerificaTokenBDD.getSingleResult();
                if (tokenFromDB != null && codSeguridad.getCodaccess_codigo_temporal() != null &&
                        codSeguridad.getCodaccess_codigo_temporal().equals(tokenFromDB.trim())) {
                    String sqlDatosCorreoIngreso = "SELECT clien_ape_clien, clien_nom_clien, usvco_ema_usvco, usvco_tlf_usvco, clien_ide_clien, clien_cod_clien, usvco_tip_usvco "
                            +
                            "FROM cnxclien, andusvco WHERE clien_ide_clien = :clienIdenti  " +
                            "AND usvco_ide_usvco = :cliacUsuVirtu AND clien_ide_clien = usvco_ide_clien";
                    Query resulDatosCorreoIngreso = entityManager.createNativeQuery(sqlDatosCorreoIngreso);
                    resulDatosCorreoIngreso.setParameter("cliacUsuVirtu", cliacUsuVirtu);
                    resulDatosCorreoIngreso.setParameter("clienIdenti", rucIdenClie);

                    List<Object[]> results2 = resulDatosCorreoIngreso.getResultList();
                    for (Object[] row2 : results2) {
                        String clienApellidos = row2[0].toString().trim();
                        String clienNombres = row2[1].toString().trim();
                        String clienEmail = row2[2].toString().trim();
                        String clienNumero = row2[3].toString().trim();

                        int tip_usvco = Integer.parseInt(row2[6].toString().trim());
                        String des_tip;
                        if (tip_usvco == 1) {
                            des_tip = "AUTORIZADOR";
                        } else {
                            des_tip = "OPERADOR";
                        }

                        String sqlUpdateToken = "UPDATE vircodaccess " +
                                "SET codaccess_estado = :estado_up " +
                                "WHERE codaccess_cedula = :cedula " +
                                "AND codaccess_usuario = :usuario " +
                                "AND codsms_codigo = :codsms " +
                                "AND codaccess_estado = :estado";

                        Query queryUpdateToken = entityManager.createNativeQuery(sqlUpdateToken);
                        queryUpdateToken.setParameter("estado_up", 0);
                        queryUpdateToken.setParameter("codsms", 8);
                        queryUpdateToken.setParameter("cedula", rucIdenClie);
                        queryUpdateToken.setParameter("usuario", cliacUsuVirtu);
                        queryUpdateToken.setParameter("estado", 1);

                        int rowsUpdated = queryUpdateToken.executeUpdate();

                        if (rowsUpdated == 0) {
                            response.put("success", false);
                            response.put("message", "No se pudo actualizar el estado del código temporal");
                            response.put("status", "AA024");
                            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                        }

                        allData.put("status", "AUTHO");
                        allData.put("des_tip_usvco", des_tip);
                        allData.put("message", "Código verificado con éxito, pendiente términos y condiciones.");
                        allDataList.add(allData);
                        response.put("AllData", allDataList);
                        return new ResponseEntity<>(response, HttpStatus.OK);
                    }

                    intentosRealizadoTokenFallos = 0;
                } else {
                    intentosRealizadoTokenFallos++;
                    if (intentosRealizadoTokenFallos >= 3) {
                        String sqlBloqUser = "UPDATE andusvco SET usvco_ctr_bloq = :bloqueo WHERE usvco_ide_clien = :rudIdenClie AND usvco_ide_usvco = :ideClieUsu";
                        Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                        resultBloqUser.setParameter("bloqueo", "0");
                        resultBloqUser.setParameter("rudIdenClie", rucIdenClie);
                        resultBloqUser.setParameter("ideClieUsu", cliacUsuVirtu);

                        try {
                            int rowsUpdated = resultBloqUser.executeUpdate();
                            if (rowsUpdated > 0) {
                                String sqlDatosCorreoIngreso = "SELECT usvco_nom_usvco, usvco_ema_usvco FROM andusvco WHERE usvco_ide_clien = :usvco_ide_clien AND usvco_ide_usvco = :usvco_ide_usvco";
                                Query resulDatosCorreoIngreso = entityManager.createNativeQuery(sqlDatosCorreoIngreso);
                                resulDatosCorreoIngreso.setParameter("usvco_ide_clien", rucIdenClie);
                                resulDatosCorreoIngreso.setParameter("usvco_ide_usvco", cliacUsuVirtu);
                                Libs fechaHoraService = new Libs(entityManager);
                                String FechaHora = fechaHoraService.obtenerFechaYHora();

                                List<Object[]> results2 = resulDatosCorreoIngreso.getResultList();
                                for (Object[] row2 : results2) {
                                    String clienNombres = row2[0].toString().trim();
                                    String clienEmail = row2[1].toString().trim();
                                    String IpIngreso = codSeguridad.getIpterminal();
                                    sendEmail emailBloq = new sendEmail();
                                    emailBloq.sendEmailBloqueo("", clienNombres, FechaHora, clienEmail, IpIngreso);

                                    String accesoDipTermi = codSeguridad.getIpterminal();
                                    String accesoMacTermi = " ";
                                    Libs fechaHoraService2 = new Libs(entityManager);
                                    String accesoFecAcces = fechaHoraService2.obtenerFechaYHora();
                                    String accesoCodAcces = generarNumberoSerial(1000000, 99999999);
                                    String accesoDesUsuar = cliacUsuVirtu;
                                    String accesoCodTacce = "2";
                                    System.out.println(accesoCodAcces);
                                    String sqlInsertAccesos = "INSERT INTO andacceso VALUES (:acceso_cod_acces, :acceso_des_usuar, :acceso_pas_usuar, :acceso_fec_acces, :acceso_dip_termi, :acceso_mac_termi, :acceso_cod_tacce)";
                                    Query resultInsertAcceso = entityManager.createNativeQuery(sqlInsertAccesos);
                                    resultInsertAcceso.setParameter("acceso_cod_acces", accesoCodAcces);
                                    resultInsertAcceso.setParameter("acceso_des_usuar", accesoDesUsuar);
                                    resultInsertAcceso.setParameter("acceso_pas_usuar", "");
                                    resultInsertAcceso.setParameter("acceso_fec_acces", accesoFecAcces);
                                    resultInsertAcceso.setParameter("acceso_dip_termi", accesoDipTermi);
                                    resultInsertAcceso.setParameter("acceso_mac_termi", accesoMacTermi);
                                    resultInsertAcceso.setParameter("acceso_cod_tacce", accesoCodTacce);
                                    resultInsertAcceso.executeUpdate();

                                    intentosRealizadoTokenFallos = 0;

                                    String sqlUpdatesToken = "UPDATE vircodaccess " +
                                            "SET codaccess_estado = :estado_up " +
                                            "WHERE codaccess_cedula = :cedula " +
                                            "AND codaccess_usuario = :usuario " +
                                            "AND codsms_codigo = :codsms " +
                                            "AND codaccess_estado = :estado";

                                    Query queryUpdatesToken = entityManager.createNativeQuery(sqlUpdatesToken);
                                    queryUpdatesToken.setParameter("estado_up", 0);
                                    queryUpdatesToken.setParameter("codsms", 8);
                                    queryUpdatesToken.setParameter("cedula", rucIdenClie);
                                    queryUpdatesToken.setParameter("usuario", cliacUsuVirtu);
                                    queryUpdatesToken.setParameter("estado", 1);

                                    int rowsUpdatesd = queryUpdatesToken.executeUpdate();

                                    if (rowsUpdatesd == 0) {
                                        response.put("success", false);
                                        response.put("message", "No se pudo actualizar el estado del código temporal");
                                        response.put("status", "AA024");
                                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                                    }

                                    Map<String, Object> data = new HashMap<>();
                                    data.put("message", "Usuario bloqueado por exceder límite de intentos");
                                    data.put("status", "AA025");

                                    response.put("success", false);
                                    response.put("AllData", List.of(data));

                                    status = HttpStatus.BAD_REQUEST;

                                }
                            }
                        } catch (Exception e) {
                            // kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback del
                            // UPDATE/INSERT de bloqueo en validarCodSeguridad][][2026-05-21]
                            throw new RuntimeException("Error al intentar bloquear el usuario: " + e.getMessage(), e);
                        }
                    } else {
                        response.put("success", false);
                        response.put("message", "Código temporal incorrecto. Intentos restantes: "
                                + (3 - intentosRealizadoTokenFallos));
                        response.put("status", "AA058");
                        status = HttpStatus.BAD_REQUEST;

                    }
                }
            } else {
                allData.put("status", "AA027");
                allData.put("errors", "CODIGO TEMPORAL EXPIRADO, POR EXCEDER LOS 4 MINUTOS");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            return new ResponseEntity<>(response, status);
        } catch (Exception e) {
            // kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback de
            // los UPDATEs/INSERTs en validarCodSeguridad][][2026-05-21]
            throw new RuntimeException("Error interno del servidor en validarCodSeguridad: " + e.getMessage(), e);
        }
    }

    public String validarCodigoSeguridad(CodSegurdiad request) {

        if (request.getCodaccess_codigo_temporal() == null || request.getCodaccess_codigo_temporal().trim().isEmpty()) {
            return "El código temporal no puede estar vacío o contener solo espacios.";
        }

        if (request.getCodaccess_codigo_temporal().length() < 6) {
            return "El código temporal debe tener al menos 6 caracteres.";
        }

        return null;
    }

    public String codigoAleatorioTemp() {
        // Genera un número aleatorio de 6 dígitos
        Random random = new Random();
        int numeroAleatorio = 100000 + random.nextInt(900000); // Asegura 6 dígitos
        return String.valueOf(numeroAleatorio);
    }

    public ResponseEntity<Map<String, Object>> aceptarTerminosCondiciones(HttpServletRequest request,
            Authentication authentication, Map<String, String> body) {
        try {
            Map<String, Object> allData = new HashMap<>();
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> allDataList = new ArrayList<>();

            String token = Obtenertoken.desdeCookie(request);
            if (token == null) {
                response.put("success", false);
                allData.put("status", "AA027");
                allData.put("errors", "No autorizado: no fue posible generar el token de acceso.");
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (authentication == null || !authentication.isAuthenticated()) {
                response.put("success", false);
                response.put("message", "No autorizado");
                allData.put("status", "AA028");
                allData.put("errors", "La sesión no es válida o ha expirado.");
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String cliacUsuVirtu = authentication.getName();
            String rucIdenClie = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (cliacUsuVirtu == null || rucIdenClie == null || numSocio == null) {
                allData.put("message", "Datos del token incompletos");
                allData.put("status", "AA022");
                allData.put("errors", "ERROR EN LA AUTENTICACIÓN");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            // Obtener los datos del usuario para el SMS y Email
            String sqlDatosCorreoIngreso = "SELECT clien_ape_clien, clien_nom_clien, usvco_ema_usvco, usvco_tlf_usvco, clien_ide_clien, clien_cod_clien, usvco_tip_usvco "
                    +
                    "FROM cnxclien, andusvco WHERE clien_ide_clien = :clienIdenti  " +
                    "AND usvco_ide_usvco = :cliacUsuVirtu AND clien_ide_clien = usvco_ide_clien";
            Query resulDatosCorreoIngreso = entityManager.createNativeQuery(sqlDatosCorreoIngreso);
            resulDatosCorreoIngreso.setParameter("cliacUsuVirtu", cliacUsuVirtu);
            resulDatosCorreoIngreso.setParameter("clienIdenti", rucIdenClie);

            List<Object[]> results2 = resulDatosCorreoIngreso.getResultList();
            if (results2.isEmpty()) {
                allData.put("message", "Usuario no encontrado en la base de datos");
                allData.put("status", "AA029");
                allData.put("errors", "ERROR EN LA AUTENTICACIÓN");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            Object[] row2 = results2.get(0);
            String clienApellidos = row2[0].toString().trim();
            String clienNombres = row2[1].toString().trim();
            String clienEmail = row2[2].toString().trim();
            String clienNumero = row2[3].toString().trim();

            int tip_usvco = Integer.parseInt(row2[6].toString().trim());
            String des_tip = (tip_usvco == 1) ? "AUTORIZADOR" : "OPERADOR";

            String ipIngresoLogin = (body != null) ? body.get("ipterminal") : null;
            if (ipIngresoLogin == null) {
                ipIngresoLogin = request.getRemoteAddr();
            }

            Libs fechaHoraService = new Libs(entityManager);
            String FechaIngresoLogin = fechaHoraService.obtenerFechaYHora();

            // Enviar SMS y Correo
            SendSMS sms = new SendSMS();
            sms.sendVirtualAccessSMS(clienNumero, "1150", "VIRTUALCOP", FechaIngresoLogin);
            sendEmail enviarCorreo = new sendEmail();
            enviarCorreo.sendEmailInicioSesion(clienApellidos, clienNombres, FechaIngresoLogin, ipIngresoLogin,
                    clienEmail);

            // Activación definitiva de la sesión y purga de previas
            try {
                String tokenSession = Obtenertoken.desdeCookie(request);
                String sessionId = jwtUtil.getSessionIdFromToken(tokenSession);
                if (sessionId != null) {
                    String sqlInvalida = "UPDATE andctrlvirlogin SET ctrlvirlogin_ctrl_virtual = 0 " +
                            "WHERE ctrlvirlogin_ide_virtual = :ide AND ctrlvirlogin_user_virtual = :user AND ctrlvirlogin_cod_temporal != :uuid";
                    Query qInv = entityManager.createNativeQuery(sqlInvalida);
                    qInv.setParameter("ide", rucIdenClie);
                    qInv.setParameter("user", cliacUsuVirtu);
                    qInv.setParameter("uuid", sessionId);
                    qInv.executeUpdate();

                    String sqlActiva = "UPDATE andctrlvirlogin SET ctrlvirlogin_ctrl_virtual = 1, ctrlvirlogin_fecha_virtual = CURRENT "
                            +
                            "WHERE ctrlvirlogin_cod_temporal = :uuid";
                    Query qAct = entityManager.createNativeQuery(sqlActiva);
                    qAct.setParameter("uuid", sessionId);
                    qAct.executeUpdate();
                }
            } catch (Exception ex) {
                // kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback de
                // los UPDATEs en aceptarTerminosCondiciones y evite
                // UnexpectedRollbackException][][2026-05-21]
                throw new RuntimeException("Error al activar sesion en andctrlvirlogin: " + ex.getMessage(), ex);
            }

            allData.put("status", "AUTHO");
            allData.put("des_tip_usvco", des_tip);
            allData.put("message", "Inicio de sesion exitoso!");
            allDataList.add(allData);
            response.put("AllData", allDataList);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            // kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback de
            // los UPDATEs en aceptarTerminosCondiciones][][2026-05-21]
            throw new RuntimeException("Error interno del servidor en aceptarTerminosCondiciones: " + e.getMessage(),
                    e);
        }
    }

    public static String generarNumberoSerial(int min, int max) {
        Random random = new Random();
        int randomNumber = random.nextInt((max - min) + 1) + min;
        return String.valueOf(randomNumber);
    }

}
