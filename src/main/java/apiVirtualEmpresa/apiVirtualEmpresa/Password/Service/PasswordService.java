package apiVirtualEmpresa.apiVirtualEmpresa.Password.Service;

import apiVirtualEmpresa.apiVirtualEmpresa.Password.dto.PasswordUtils;
import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.TokenExpirationService;
import envioCorreo.sendEmail;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import libs.PassSecure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import sms.SendSMS;

import java.util.*;

//import apiVirtualEmpresa.apiVirtualEmpresa.libs.Libs;

@Transactional
@Service

public class PasswordService {


    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TokenExpirationService tokenExpirationService;

    private int intentosRealizadoTokenFallos = 0;

    public PasswordService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }


    //cambio de contraseña
    public ResponseEntity<Map<String, Object>> cambioPassword(HttpServletRequest request, @RequestBody PasswordUtils dto, Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {
            String token = Obtenertoken.desdeCookie(request);

            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("message", "La sesión no es válida o ha expirado.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String cliacUsuRuc = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            String passwordConf = dto.getPasswordConf();
            String passwordNuev = dto.getPasswordNuev();
            String passwordAct = dto.getPasswordAct();
            String codTemp = dto.getCodTemp();

            if (cliacUsuRuc == null || clienIdenti == null || numSocio == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA34050");
                err.put("message", "Datos del token incompletos");
                err.put("error", "ERROR EN LA AUTENTICACIÓN");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            if (passwordConf == null || !passwordConf.matches("\\d{4}") ||
                    passwordNuev == null || !passwordNuev.matches("\\d{4}") ||
                    passwordAct == null || !passwordAct.matches("\\d{4}")) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763309");
                err.put("message", "La contraseña debe ser de 4 caracteres numéricos.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            String sqlpassword =
                    "SELECT usvco_psw_usvco, usvco_psw_cntrl FROM andusvco " +
                            "WHERE usvco_ide_clien = :usvco_ide_clien " +
                            "AND usvco_ide_usvco = :usvco_ide_usvco";

            Query ressqlPassword = entityManager.createNativeQuery(sqlpassword);
            ressqlPassword.setParameter("usvco_ide_clien", clienIdenti);
            ressqlPassword.setParameter("usvco_ide_usvco", cliacUsuRuc);

            List<Object[]> lista = ressqlPassword.getResultList();

            if (lista.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763300");
                err.put("message", "Usuario no encontrado");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            if (!passwordConf.equals(passwordNuev)) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763309");
                err.put("message", "LA CONTRASEÑA NUEVA NO COINCIDE CON LA CONTRASEÑA DE CONFIRMACIÓN.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            if (passwordNuev.equals(passwordAct)) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763309");
                err.put("message", "LA CONTRASEÑA NUEVA NO DEBE SER IGUAL A LA ANTERIOR.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            PassSecure passSecure = new PassSecure();
            String password = passSecure.encryptPassword(passwordConf);


            String sqlVerificaTokenBDD =
                    "SELECT codaccess_codigo_temporal " +
                            "FROM vircodaccess " +
                            "WHERE codaccess_cedula = :cedula " +
                            "AND codaccess_usuario = :usuario " +
                            "AND codaccess_estado = :estado " +
                            "AND codsms_codigo = :codsms";

            Query qToken = entityManager.createNativeQuery(sqlVerificaTokenBDD);
            qToken.setParameter("codsms", 13);
            qToken.setParameter("estado", 1);
            qToken.setParameter("cedula", clienIdenti);
            qToken.setParameter("usuario", cliacUsuRuc);

            List<String> tokens = qToken.getResultList();
            if (tokens.isEmpty()) {
                response.put("message", "CODIGO TEMPORAL EXPIRADO, POR EXCEDER LOS 4 MINUTOS");
                response.put("status", "AA1879");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String tokenFromDB = tokens.get(0).trim();

            if (!tokenFromDB.equals(dto.getCodTemp())) {
                intentosRealizadoTokenFallos++;
                if (intentosRealizadoTokenFallos >= 3) {

                    String sqlUpdateEstado =
                            "UPDATE andusvco SET usvco_ctr_bloq = :estado " +
                                    "WHERE usvco_ide_clien = :ideClien " +
                                    "AND usvco_ide_usvco = :ideUsvco";

                    Query queryUpdateestado = entityManager.createNativeQuery(sqlUpdateEstado);
                    queryUpdateestado.setParameter("estado", 0);
                    queryUpdateestado.setParameter("ideClien", clienIdenti);
                    queryUpdateestado.setParameter("ideUsvco", cliacUsuRuc);

                    int rowsUpdatede = queryUpdateestado.executeUpdate();

                    if (rowsUpdatede == 0) {
                        Map<String, Object> err = new HashMap<>();
                        err.put("status", "AA027");
                        err.put("message", "NO SE PUEDO BLOQUEAR EL USUARIO.");
                        allDataList.add(err);
                        response.put("success", false);
                        response.put("AllData", allDataList);
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }

                    intentosRealizadoTokenFallos = 0;

                    Map<String, Object> ok = new HashMap<>();
                    ok.put("status", "AA025");
                    ok.put("message", "USUARIO BLOQUEADO POR EXCEDER EL NUMERO DE INTENTOS PERMITIDOS");
                    allDataList.add(ok);
                    response.put("success", false);
                    response.put("AllData", allDataList);
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }

                Map<String, Object> warn = new HashMap<>();


                warn.put("status", "AA029");
                warn.put("message", "Código temporal incorrecto. Intentos restantes: " + (3 - intentosRealizadoTokenFallos));
                allDataList.add(warn);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);

            } else {
                String sqlUpdatePassword =
                        "UPDATE andusvco SET usvco_psw_usvco = :nuevaPsw, " +
                                "usvco_psw_cntrl = :nuevaPsw " +
                                "WHERE usvco_ide_clien = :ideClien " +
                                "AND usvco_ide_usvco = :ideUsvco";

                Query queryUpdatePassword = entityManager.createNativeQuery(sqlUpdatePassword);
                queryUpdatePassword.setParameter("nuevaPsw", password);
                queryUpdatePassword.setParameter("ideClien", clienIdenti);
                queryUpdatePassword.setParameter("ideUsvco", cliacUsuRuc);

                int rowsUpdated = queryUpdatePassword.executeUpdate();

                if (rowsUpdated == 0) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "AA027");
                    err.put("message", "No se pudo actualizar la contraseña.");
                    allDataList.add(err);
                    response.put("success", false);
                    response.put("AllData", allDataList);
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                intentosRealizadoTokenFallos = 0;
                Map<String, Object> ok = new HashMap<>();
                ok.put("status", "PWD001");
                ok.put("message", "CONTRASEÑA ACTUALIZADA CON ÉXITO");
                allDataList.add(ok);
                response.put("success", true);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }


        } catch (Exception e) {
            //kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback del UPDATE en cambioPassword][][2026-05-21]
            throw new RuntimeException("Error interno del servidor en cambioPassword: " + e.getMessage(), e);
        }
    }

    public ResponseEntity<Map<String, Object>> codcambioPassword(HttpServletRequest request, @RequestBody PasswordUtils dto, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {


            String token = Obtenertoken.desdeCookie(request);

            String rucUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);


            // Validación de datos del token
            if (rucUsuVirtu == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos ");
                response.put("status", "AA1767");
                response.put("error", "ERROR EN LA AUTENTICACIÓN");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            String passwordConf = dto.getPasswordConf();
            String passwordNuev = dto.getPasswordNuev();
            String passwordAct = dto.getPasswordAct();

            if (passwordConf == null || !passwordConf.matches("\\d{4}") ||
                    passwordNuev == null || !passwordNuev.matches("\\d{4}") ||
                    passwordAct == null || !passwordAct.matches("\\d{4}")) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763309");
                err.put("message", "La contraseña debe ser de 4 caracteres numéricos.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String sqlpassword =
                    "SELECT usvco_psw_usvco, usvco_psw_cntrl FROM andusvco " +
                            "WHERE usvco_ide_clien = :usvco_ide_clien " +
                            "AND usvco_ide_usvco = :usvco_ide_usvco";

            Query ressqlPassword = entityManager.createNativeQuery(sqlpassword);
            ressqlPassword.setParameter("usvco_ide_clien", clienIdenti);
            ressqlPassword.setParameter("usvco_ide_usvco", rucUsuVirtu);

            List<Object[]> lista = ressqlPassword.getResultList();

            if (lista.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763300");
                err.put("message", "Usuario no encontrado");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            Object[] row = lista.get(0);


            String clienWwwPswrd = row[1] != null ? row[1].toString().trim() : null;

            if (clienWwwPswrd == null || clienWwwPswrd.isEmpty()) {
                response.put("success", false);
                response.put("message", "Contraseña no válida en base de datos");
                response.put("status", "ERRORPSW01");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            clienWwwPswrd = clienWwwPswrd.replace("\"", "");


            PassSecure passSecure = new PassSecure();
            String passDec = passSecure.decryptPassword(clienWwwPswrd);


            if (passDec != null) {
                passDec = passDec.trim().replace("\"", "");
            }


            if (!passDec.equals(passwordAct)) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763309");
                err.put("message", "LA CONTRASEÑA ACTUAL ES INCORRECTA.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (!passwordConf.equals(passwordNuev)) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763309");
                err.put("message", "LA CONTRASEÑA NUEVA NO COINCIDE CON LA CONTRASEÑA DE CONFIRMACIÓN.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            if (passwordNuev.equals(passDec)) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR763309");
                err.put("message", "LA CONTRASEÑA NUEVA NO DEBE SER IGUAL A LA ANTERIOR.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            apiVirtualEmpresas.virtualempresas.libs.Libs fechaHoraService = new apiVirtualEmpresas.virtualempresas.libs.Libs(entityManager);
            String fecha = fechaHoraService.obtenerFechaYHora();

            String sqlQuery = "SELECT FIRST 1 clien_cod_empre, clien_cod_ofici, usvco_tlf_usvco, usvco_ema_usvco, clien_nom_clien, clien_ape_clien " +
                    "FROM cnxctadp, cnxclien, andusvco " +
                    "WHERE clien_ide_clien = :ide_clien " +
                    "AND usvco_ide_usvco = :usuario " +
                    "AND usvco_ctr_bloq = '1'";


            // Consulta cuenta origen
            Query query = entityManager.createNativeQuery(sqlQuery);
            query.setParameter("ide_clien", clienIdenti);
            query.setParameter("usuario", rucUsuVirtu);
            List<Object[]> results = query.getResultList();


            // Procesar resultados cuenta origen
            if (results.isEmpty()) {
                response.put("message", "Usuario no encontrado o bloqueado clienIdenti ");
                response.put("status", "ERROR7780");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Extraer datos de las cuentas
            Object[] resultEnvio = results.get(0);

            String tlfCtaEnvio = resultEnvio[2].toString().trim();
            String emailCtaEnvio = resultEnvio[3].toString().trim();
            String nombreCtaEnvio = resultEnvio[4].toString().trim();
            String apellCtaEnvio = resultEnvio[5].toString().trim();


            //generar codigo
            String CodigoTrfDirectas = codigoAleatorio6Temp();
            SendSMS smsDesbloqueo = new SendSMS();
            smsDesbloqueo.sendSecurityCodeSMS(tlfCtaEnvio, "1150", CodigoTrfDirectas, "Actualizar su Clave", fecha);
            // Enviar correo
            sendEmail enviarCorreo = new sendEmail();
            enviarCorreo.sendEmailTokenTemp(apellCtaEnvio, nombreCtaEnvio, fecha, emailCtaEnvio, CodigoTrfDirectas);


            String sqlBloqUser = "UPDATE vircodaccess SET codaccess_estado = '0' WHERE codaccess_cedula = :rudIdenClie AND codaccess_usuario = :ideClieUsu AND codaccess_estado = '1' AND codsms_codigo = 13";
            Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
            resultBloqUser.setParameter("rudIdenClie", clienIdenti);
            resultBloqUser.setParameter("ideClieUsu", rucUsuVirtu);
            resultBloqUser.executeUpdate();

            // Insertar nuevo código temporal
            String sqlInsertAccesos = "INSERT INTO vircodaccess "
                    + "(codaccess_cedula, codaccess_usuario, codaccess_codigo_temporal, codsms_codigo, codaccess_estado, codaccess_fecha) "
                    + "VALUES (:codaccess_cedula, :codaccess_usuario, :codaccess_codigo_temporal, :codsms_codigo, :codaccess_estado, :codaccess_fecha)";

            Query resultInsertAcceso = entityManager.createNativeQuery(sqlInsertAccesos);
            resultInsertAcceso.setParameter("codaccess_cedula", clienIdenti);
            resultInsertAcceso.setParameter("codaccess_usuario", rucUsuVirtu);
            resultInsertAcceso.setParameter("codaccess_codigo_temporal", CodigoTrfDirectas);
            resultInsertAcceso.setParameter("codsms_codigo", "13");
            resultInsertAcceso.setParameter("codaccess_estado", "1");
            resultInsertAcceso.setParameter("codaccess_fecha", fecha);
            resultInsertAcceso.executeUpdate();
            tokenExpirationService.programarExpiracionToken(clienIdenti, CodigoTrfDirectas, "13");
            response.put("message", "CODIGO GENERADO CON EXITO ");
            response.put("status", "CODTRFOK005");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            //kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback de los UPDATEs/INSERTs en codcambioPassword][][2026-05-21]
            throw new RuntimeException("Error interno del servidor en codcambioPassword: " + e.getMessage(), e);
        }

    }

    public String codigoAleatorio6Temp() {
        // Genera un número aleatorio de 6 dígitos
        Random random = new Random();
        int numeroAleatorio = 100000 + random.nextInt(900000);
        return String.valueOf(numeroAleatorio);
    }
}
