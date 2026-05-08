package apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.Service;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.TokenExpirationService;
import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasDirect.dto.TransfDirectUtils;
import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import sms.SendSMS;
import envioCorreo.sendEmail;
import apiVirtualEmpresas.virtualempresas.libs.Libs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import libs.PassSecure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
@Service

@Transactional

public class TransfDirectService {

    @PersistenceContext
    private EntityManager entityManager;

    private final JwtUtil jwtUtil;
    public TransfDirectService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Autowired
    private TokenExpirationService tokenExpirationService;

    int intentosRealizadoTokenFallos = 0;


    public ResponseEntity<Map<String, Object>> srtGrabarDir(HttpServletRequest request, Authentication authentication, TransfDirectUtils dto) {

        List<Map<String, Object>> allDataList = new ArrayList<>();
        try {


            Map<String, Object> response = new HashMap<>();
            String token = Obtenertoken.desdeCookie(request);

            String cliacUsuRuc = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            // Validación de datos del token
            if (cliacUsuRuc == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos");
                response.put("status", "AA022");
                response.put("error", "ERROR EN LA AUTENTICACIÓN");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            // VERIFICAR SI EL USUARIO ESTÁ BLOQUEADO
            String sqlBloqueo =
                    "SELECT usvco_ctr_bloq " +
                            "FROM andusvco " +
                            "WHERE usvco_ide_clien = :ideClien " +
                            "AND usvco_ide_usvco = :ideUsu";

            Query queryBloqueo = entityManager.createNativeQuery(sqlBloqueo);
            queryBloqueo.setParameter("ideClien", clienIdenti);
            queryBloqueo.setParameter("ideUsu", cliacUsuRuc);

            Object bloqueoResult = queryBloqueo.getSingleResult();

            if (bloqueoResult == null || !"1".equals(bloqueoResult.toString().trim())) {
                response.put("success", false);
                response.put("message", "Usuario se encuentra bloqueado");
                response.put("status", "AA025");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            String numeroCuentaEnvio = dto.getCtaEnvio();
            String numeroCtaDestino = dto.getCtaDestino();
            String descripcionTrf = dto.getTxtdettrnsf();
            Float valTransferencia = dto.getValtrans();

            if (numeroCuentaEnvio == null || !numeroCuentaEnvio.matches("\\d{12}")) {
                response.put("message", "El número de cuenta origen debe tener exactamente 12 dígitos numéricos.");
                response.put("status", "ERROR002");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (numeroCtaDestino == null || !numeroCtaDestino.matches("\\d{12}")) {
                response.put("message", "El número de cuenta destino debe tener exactamente 12 dígitos numéricos.");
                response.put("status", "ERROR003");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (dto.getCodTempTransDirec() == null || !dto.getCodTempTransDirec().matches("\\d{6}")) {
                response.put("message", "Código de seguridad inválido");
                response.put("status", "AA023");
                response.put("error", "El código debe contener exactamente 6 dígitos");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (descripcionTrf == null || descripcionTrf.trim().isEmpty() || descripcionTrf.length() > 250) {
                response.put("message", "La descripción de la transferencia no puede estar vacía y debe tener como máximo 250 caracteres.");
                response.put("status", "ERROR004");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (valTransferencia == null || valTransferencia <= 0 || !valTransferencia.toString().matches("^\\d{1,14}(\\.\\d{1,2})?$")) {
                response.put("message", "El monto de la transferencia debe ser un número positivo con hasta 14 dígitos enteros y 2 decimales.");
                response.put("status", "ERROR005");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String sqlVerificaTokenBDD = "SELECT FIRST 1 codaccess_codigo_temporal FROM vircodaccess " +
                    "WHERE codaccess_cedula = :codaccess_cedula AND codaccess_usuario = :codaccess_usuario " +
                    "AND codaccess_estado = :codaccess_estado AND codsms_codigo = '9' ORDER BY codaccess_id DESC  ";
            Query queryVerificaTokenBDD = entityManager.createNativeQuery(sqlVerificaTokenBDD);

            queryVerificaTokenBDD.setParameter("codaccess_cedula", clienIdenti);
            queryVerificaTokenBDD.setParameter("codaccess_usuario", cliacUsuRuc);
            queryVerificaTokenBDD.setParameter("codaccess_estado", "1");

            List<?> resultsTokenBDD = queryVerificaTokenBDD.getResultList();

            if (resultsTokenBDD.isEmpty()) {
                response.put("message", "CODIGO TEMPORAL EXPIRADO, POR EXCEDER LOS 4 MINUTOS");
                response.put("status", "AA027");
                response.put("success", false);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            String saldoDisponible = obtenerSaldoDisponible(numeroCuentaEnvio);
            BigDecimal saldoDispoParse = new BigDecimal(saldoDisponible);

            String tokenFromDB = resultsTokenBDD.get(0).toString().trim();
            String tokenRecibido = dto.getCodTempTransDirec().trim();
            if (!tokenFromDB.equals(tokenRecibido)) {
                intentosRealizadoTokenFallos++;
                if (intentosRealizadoTokenFallos >= 3) {
                    // Bloquear usuario
                    String sqlBloqUser = "UPDATE andusvco SET usvco_ctr_bloq = :bloqueo WHERE usvco_ide_clien = :rudIdenClie AND usvco_ide_usvco = :ideClieUsu";
                    Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                    resultBloqUser.setParameter("bloqueo", "0");
                    resultBloqUser.setParameter("rudIdenClie", clienIdenti);
                    resultBloqUser.setParameter("ideClieUsu",cliacUsuRuc);

                    try {
                        int rowsUpdated = resultBloqUser.executeUpdate();
                        if (rowsUpdated > 0) {
                            // Obtener datos para el correo
                            String sqlDatosCorreoIngreso = "SELECT usvco_nom_usvco, usvco_ema_usvco FROM andusvco WHERE usvco_ide_clien = :usvco_ide_clien AND usvco_ide_usvco = :usvco_ide_usvco";
                            Query resulDatosCorreoIngreso = entityManager.createNativeQuery(sqlDatosCorreoIngreso);
                            resulDatosCorreoIngreso.setParameter("usvco_ide_clien", clienIdenti);
                            resulDatosCorreoIngreso.setParameter("usvco_ide_usvco", cliacUsuRuc);
                            Libs fechaHoraService = new Libs(entityManager);
                            String FechaHora = fechaHoraService.obtenerFechaYHora();

                            List<Object[]> results2 = resulDatosCorreoIngreso.getResultList();

                            for (Object[] row2 : results2) {
                                String clienNombres = row2[0].toString().trim();
                                String clienEmail = row2[1].toString().trim();
                                String IpIngreso = dto.getIpterminal();
                                sendEmail emailBloq = new sendEmail();
                                emailBloq.sendEmailBloqueo("", clienNombres, FechaHora, clienEmail, IpIngreso);
                            }

                            String sqlUpdatesToken =
                                    "UPDATE vircodaccess " +
                                            "SET codaccess_estado = :estado_up " +
                                            "WHERE codaccess_cedula = :cedula " +
                                            "AND codaccess_usuario = :usuario " +
                                            "AND codsms_codigo = :codsms " +
                                            "AND codaccess_estado = :estado";

                            Query queryUpdatesToken = entityManager.createNativeQuery(sqlUpdatesToken);
                            queryUpdatesToken.setParameter("estado_up", 0);
                            queryUpdatesToken.setParameter("codsms", 9);
                            queryUpdatesToken.setParameter("cedula", clienIdenti);
                            queryUpdatesToken.setParameter("usuario", cliacUsuRuc);
                            queryUpdatesToken.setParameter("estado", 1);

                            int rowsUpdatesd = queryUpdatesToken.executeUpdate();

                            if (rowsUpdatesd == 0) {
                                response.put("success", false);
                                response.put("message", "No se pudo actualizar el estado del código temporal");
                                response.put("status", "AA024");
                                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                            }

                            intentosRealizadoTokenFallos = 0;
                            response.put("message", "Usuario bloqueado por exceder límite de intentos");
                            response.put("status", "AA025");
                            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                        }
                    } catch (Exception e) {
                        response.put("message", "Error al intentar bloquear el usuario");
                        response.put("status", "AA024");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }
                } else {
                    response.put("message", "Código temporal incorrecto. Intentos restantes: " + (3 - intentosRealizadoTokenFallos));
                    response.put("status", "AA023");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
            }

            intentosRealizadoTokenFallos = 0;

            Float valTransFloat = dto.getValtrans();
            BigDecimal valTransferencias = BigDecimal.valueOf(valTransFloat);

            if (saldoDispoParse.compareTo(valTransferencias) >= 0) {


                String sqlQuery = "SELECT clien_cod_empre, clien_cod_ofici, ctadp_cod_ctadp " +
                        "FROM cnxctadp, cnxclien, andusvco " +
                        "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                        "AND ctadp_cod_depos IN (1,2,9) " +
                        "AND ctadp_cod_ectad = :ctadp_cod_ectad " +
                        "AND ctadp_cod_clien = clien_cod_clien " +
                        "AND clien_ide_clien = usvco_ide_clien";

                String sqlQuery2 = "SELECT clien_cod_empre, clien_cod_ofici, ctadp_cod_ctadp " +
                        "FROM cnxctadp, cnxclien " +
                        "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                        "AND ctadp_cod_depos IN (1,2,9) " +
                        "AND ctadp_cod_ectad = :ctadp_cod_ectad " +
                        "AND ctadp_cod_clien = clien_cod_clien " ;

                // Consulta cuenta origen
                Query query = entityManager.createNativeQuery(sqlQuery);
                query.setParameter("ctadp_cod_ctadp", numeroCuentaEnvio);
                query.setParameter("ctadp_cod_ectad", "1");
                List<Object[]> results = query.getResultList();
                // Consulta cuenta destino
                Query query1 = entityManager.createNativeQuery(sqlQuery2);
                query1.setParameter("ctadp_cod_ctadp", numeroCtaDestino);
                query1.setParameter("ctadp_cod_ectad", "1");
                List<Object[]> results1 = query1.getResultList();
                // Procesar resultados cuenta origen
                if (results.isEmpty()) {
                    response.put("message", "Cuenta origen no encontrada o inválida");
                    response.put("status", "ERROR004");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
                // Procesar resultados cuenta destino
                if (results1.isEmpty()) {
                    response.put("message", "Cuenta destino no encontrada o inválida");
                    response.put("status", "ERROR005");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
                // Extraer datos de las cuentas
                Object[] resultEnvio = results.get(0);
                Object[] resultDestino = results1.get(0);
                String clienCodEmpreEnvio = resultEnvio[0].toString().trim();
                String clienCodOficiEnvio = resultEnvio[1].toString().trim();
                String ctadpCodCtadpEnvio = resultEnvio[2].toString().trim();

                String clienCodOficiDestino = resultDestino[1].toString().trim();
                String ctadpCodCtadpDestino = resultDestino[2].toString().trim();

                //OBTENER INFORMACION PARA ENVIO DE NOTIFICACION POR CORREO ELECTRONICO
                String strSqlEnvio = "select usvco_ema_usvco, TRIM(clien_ape_clien) || ' ' || TRIM(clien_nom_clien) AS nombres, usvco_tlf_usvco, usvco_tip_usvco " +
                        "from cnxclien, andusvco " +
                        "where clien_ide_clien= :clien_ide_clien " +
                        "and usvco_ide_usvco= :usvco_ide_usvco ";
                Query queryParamsInfoDes = entityManager.createNativeQuery(strSqlEnvio);
                queryParamsInfoDes.setParameter("clien_ide_clien", cliacUsuRuc);
                queryParamsInfoDes.setParameter("usvco_ide_usvco", clienIdenti);
                List<Object[]> resultsDatosCoreeo1 = queryParamsInfoDes.getResultList();
                String emailOperador = "";
                String nombresCliente = "";
                String tlfOperador = "";
                if (!resultsDatosCoreeo1.isEmpty()) {
                    Object[] rowEnvio = resultsDatosCoreeo1.get(0);
                    emailOperador = rowEnvio[0].toString().trim();
                    nombresCliente  = rowEnvio[1].toString().trim();
                    tlfOperador = rowEnvio[2].toString().trim();
                }

                //obtener datos del usuario autorizador para enviar correo
                String strSqlEnvioAut = "select usvco_ema_usvco, usvco_tlf_usvco " +
                        "from cnxclien, andusvco " +
                        "where clien_ide_clien= :clien_ide_clien " +
                        "and clien_ide_clien=usvco_ide_clien " +
                        "and usvco_tip_usvco=1 ";
                Query queryParamsInfoAut = entityManager.createNativeQuery(strSqlEnvioAut);
                queryParamsInfoAut.setParameter("clien_ide_clien", cliacUsuRuc);
                List<Object[]> resultsDatosCorreAut = queryParamsInfoAut.getResultList();
                String emailAut = "";
                String tlfAutr = "";
                if (!resultsDatosCorreAut.isEmpty()) {
                    Object[] rowEnvioAut = resultsDatosCorreAut.get(0);
                    emailAut = rowEnvioAut[0].toString().trim();
                    tlfAutr  = rowEnvioAut[1].toString().trim();
                }

                if (clienCodOficiEnvio != null
                        && clienCodOficiDestino != null
                        && clienCodOficiEnvio.trim().equals(clienCodOficiDestino.trim())) {

                    String callTransferProcedure = "CALL cnxprc_reg_trfwb(:clienCodEmpreEnvio, :clienCodOficiEnvio, '803', " +
                            ":descripcionTrf, :ctadpCodCtadpEnvio, :ctadpCodCtadpDestino, :valTransferencia)";
                    Query queryProcedure = entityManager.createNativeQuery(callTransferProcedure);
                    queryProcedure.setParameter("clienCodEmpreEnvio", clienCodEmpreEnvio);
                    queryProcedure.setParameter("clienCodOficiEnvio", clienCodOficiEnvio);
                    queryProcedure.setParameter("descripcionTrf", descripcionTrf);
                    queryProcedure.setParameter("ctadpCodCtadpEnvio", ctadpCodCtadpEnvio);
                    queryProcedure.setParameter("ctadpCodCtadpDestino", ctadpCodCtadpDestino);
                    queryProcedure.setParameter("valTransferencia", valTransferencia);

                    Object result = queryProcedure.getSingleResult();
                    int returnValue = Integer.parseInt(result.toString());

                    String sqlInfoEnvio = "SELECT ofici_nom_ofici,clien_dir_email,clien_ape_clien,clien_nom_clien, clien_tlf_celul,clien_cod_clien " +
                            "FROM cnxctadp, cnxclien, cnxofici " +
                            "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                            "AND ctadp_cod_depos IN (1,2,9) "+
                            "AND ctadp_cod_ectad= 1 " +
                            "AND clien_cod_ofici = ofici_cod_ofici "+
                            "AND ctadp_cod_clien=clien_cod_clien";
                    Query queryParamsEnvio = entityManager.createNativeQuery(sqlInfoEnvio);
                    queryParamsEnvio.setParameter("ctadp_cod_ctadp", ctadpCodCtadpEnvio);

                    String sqlInfoRecibe = "SELECT ofici_nom_ofici,clien_dir_email,clien_ape_clien,clien_nom_clien, clien_tlf_celul,clien_cod_clien " +
                            "FROM cnxctadp, cnxclien, cnxofici " +
                            "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                            "AND ctadp_cod_depos IN (1,2,9) "+
                            "AND ctadp_cod_ectad= 1" +
                            "AND clien_cod_ofici = ofici_cod_ofici "+
                            "AND ctadp_cod_clien=clien_cod_clien";
                    Query queryParamsRecibe = entityManager.createNativeQuery(sqlInfoRecibe);
                    queryParamsRecibe.setParameter("ctadp_cod_ctadp", ctadpCodCtadpDestino);

                    List<Object[]> resultsInfoCtaEnvio = queryParamsEnvio.getResultList();
                    List<Object[]> resultsInfoCtaRecibe = queryParamsRecibe.getResultList();

                    // Procesar datos de la cuenta de envío
                    if (!resultsInfoCtaEnvio.isEmpty()) {
                        Object[] rowEnvio = resultsInfoCtaEnvio.get(0); // Solo un resultado esperado
                        Map<String, String> infCtaEnvio = new HashMap<>();
                        infCtaEnvio.put("nombreOficina", rowEnvio[0].toString().trim());
                        infCtaEnvio.put("email", rowEnvio[1].toString().trim());
                        infCtaEnvio.put("apellido", rowEnvio[2].toString().trim());
                        infCtaEnvio.put("nombre", rowEnvio[3].toString().trim());
                        infCtaEnvio.put("telefono", rowEnvio[4].toString().trim());
                        infCtaEnvio.put("codigoCliente", rowEnvio[5].toString().trim());

                        response.put("informacionCtaEnvio", infCtaEnvio);
                    }
                    // Procesar datos de la cuenta de recepción
                    if (!resultsInfoCtaRecibe.isEmpty()) {
                        Object[] rowRecibe = resultsInfoCtaRecibe.get(0); // Solo un resultado esperado
                        Map<String, String> infCtaRecibe = new HashMap<>();
                        infCtaRecibe.put("nombreOficina", rowRecibe[0].toString().trim());
                        infCtaRecibe.put("email", rowRecibe[1].toString().trim());
                        infCtaRecibe.put("apellido", rowRecibe[2].toString().trim());
                        infCtaRecibe.put("nombre", rowRecibe[3].toString().trim());
                        infCtaRecibe.put("telefono", rowRecibe[4].toString().trim());
                        infCtaRecibe.put("codigoCliente", rowRecibe[5].toString().trim());
                        response.put("informacionCtaRecibe", infCtaRecibe);
                    }


                    Libs fechaHoraService = new Libs(entityManager);
                    String FechaHora = fechaHoraService.obtenerFechaYHora();


                    String numTransfer = String.valueOf(" 00000"+ returnValue);
                    String valTransf = String.valueOf(" USD. " + valTransferencia);
                    String ipterminal = dto.getIpterminal();

                    PassSecure passSecure = new PassSecure();
                    String infoConcatena = numSocio +FechaHora;
                    String encrip2 =  passSecure.encryptPassword(infoConcatena);
                    String encrip1 = encrip2.substring(0,10);

                    String sqlInsertTravir =
                            "INSERT INTO andtravir (travir_cod_socio, travir_cta_desti, travir_val_trans, travir_fec_trans, travir_num_ttran, travir_cod_encri1, travir_cod_encri2) " +
                                    "VALUES (:travir_cod_socio, :travir_cta_desti, :travir_val_trans, CURRENT, :travir_num_ttran, :travir_cod_encri1, :travir_cod_encri2)";

                    Query resultInsertTravir = entityManager.createNativeQuery(sqlInsertTravir);
                    resultInsertTravir.setParameter("travir_cod_socio", numSocio);
                    resultInsertTravir.setParameter("travir_cta_desti", ctadpCodCtadpDestino);
                    resultInsertTravir.setParameter("travir_val_trans", valTransferencia);
                    resultInsertTravir.setParameter("travir_num_ttran", returnValue);
                    resultInsertTravir.setParameter("travir_cod_encri1", encrip1);
                    resultInsertTravir.setParameter("travir_cod_encri2", encrip2);

                    resultInsertTravir.executeUpdate();
                    String sqlUpdatesToken =
                            "UPDATE vircodaccess " +
                                    "SET codaccess_estado = :estado_up " +
                                    "WHERE codaccess_cedula = :cedula " +
                                    "AND codaccess_usuario = :usuario " +
                                    "AND codsms_codigo = :codsms " +
                                    "AND codaccess_estado = :estado";

                    Query queryUpdatesToken = entityManager.createNativeQuery(sqlUpdatesToken);
                    queryUpdatesToken.setParameter("estado_up", 0);
                    queryUpdatesToken.setParameter("codsms", 9);
                    queryUpdatesToken.setParameter("cedula", clienIdenti);
                    queryUpdatesToken.setParameter("usuario", cliacUsuRuc);
                    queryUpdatesToken.setParameter("estado", 1);

                    int rowsUpdatesd = queryUpdatesToken.executeUpdate();

                    if (rowsUpdatesd == 0) {
                        response.put("success", false);
                        response.put("message", "No se pudo actualizar el estado del código temporal");
                        response.put("status", "AA024");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }

                    intentosRealizadoTokenFallos = 0;
                    response.put("message", "TRANSFERENCIA REALIZADA CON ÉXITO :)");
                    response.put("numTransferencia", returnValue);
                    response.put("status", "DTROK0005");

                } else {
                    // Transferencia entre diferentes oficinas
                    String callTransferProcedure = "CALL cnxprc_trnsf_rmtwb(:clienCodEmpreEnvio, :clienCodOficiEnvio, '803', " +
                            ":descripcionTrf, :ctadpCodCtadpEnvio, :ctadpCodCtadpDestino, :valTransferencia)";
                    Query queryProcedure = entityManager.createNativeQuery(callTransferProcedure);
                    queryProcedure.setParameter("clienCodEmpreEnvio", clienCodEmpreEnvio);
                    queryProcedure.setParameter("clienCodOficiEnvio", clienCodOficiEnvio);
                    queryProcedure.setParameter("descripcionTrf", descripcionTrf);
                    queryProcedure.setParameter("ctadpCodCtadpEnvio", ctadpCodCtadpEnvio);
                    queryProcedure.setParameter("ctadpCodCtadpDestino", ctadpCodCtadpDestino);
                    queryProcedure.setParameter("valTransferencia", valTransferencia);

                    Object result = queryProcedure.getSingleResult();
                    int returnValue = Integer.parseInt(result.toString());

                    String sqlInfoEnvio = "SELECT ofici_nom_ofici,clien_dir_email,clien_ape_clien,clien_nom_clien, clien_tlf_celul,clien_cod_clien " +
                            "FROM cnxctadp, cnxclien, cnxofici " +
                            "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                            "AND ctadp_cod_depos IN (1,2,9) "+
                            "AND ctadp_cod_ectad= 1 " +
                            "AND clien_cod_ofici = ofici_cod_ofici "+
                            "AND ctadp_cod_clien=clien_cod_clien";
                    Query queryParamsEnvio = entityManager.createNativeQuery(sqlInfoEnvio);
                    queryParamsEnvio.setParameter("ctadp_cod_ctadp", ctadpCodCtadpEnvio);

                    String sqlInfoRecibe = "SELECT ofici_nom_ofici,clien_dir_email,clien_ape_clien,clien_nom_clien, clien_tlf_celul,clien_cod_clien " +
                            "FROM cnxctadp, cnxclien, cnxofici " +
                            "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                            "AND ctadp_cod_depos IN (1,2,9) "+
                            "AND ctadp_cod_ectad= 1" +
                            "AND clien_cod_ofici = ofici_cod_ofici "+
                            "AND ctadp_cod_clien=clien_cod_clien";
                    Query queryParamsRecibe = entityManager.createNativeQuery(sqlInfoRecibe);
                    queryParamsRecibe.setParameter("ctadp_cod_ctadp", ctadpCodCtadpDestino);

                    List<Object[]> resultsInfoCtaEnvio = queryParamsEnvio.getResultList();
                    List<Object[]> resultsInfoCtaRecibe = queryParamsRecibe.getResultList();

                    // Procesar datos de la cuenta de envío
                    if (!resultsInfoCtaEnvio.isEmpty()) {
                        Object[] rowEnvio = resultsInfoCtaEnvio.get(0); // Solo un resultado esperado
                        Map<String, String> infCtaEnvio = new HashMap<>();
                        infCtaEnvio.put("nombreOficina", rowEnvio[0].toString().trim());
                        infCtaEnvio.put("email", rowEnvio[1].toString().trim());
                        infCtaEnvio.put("apellido", rowEnvio[2].toString().trim());
                        infCtaEnvio.put("nombre", rowEnvio[3].toString().trim());
                        infCtaEnvio.put("telefono", rowEnvio[4].toString().trim());
                        infCtaEnvio.put("codigoCliente", rowEnvio[5].toString().trim());

                        response.put("informacionCtaEnvio", infCtaEnvio);
                    }
                    // Procesar datos de la cuenta de recepción
                    if (!resultsInfoCtaRecibe.isEmpty()) {
                        Object[] rowRecibe = resultsInfoCtaRecibe.get(0); // Solo un resultado esperado
                        Map<String, String> infCtaRecibe = new HashMap<>();
                        infCtaRecibe.put("nombreOficina", rowRecibe[0].toString().trim());
                        infCtaRecibe.put("email", rowRecibe[1].toString().trim());
                        infCtaRecibe.put("apellido", rowRecibe[2].toString().trim());
                        infCtaRecibe.put("nombre", rowRecibe[3].toString().trim());
                        infCtaRecibe.put("telefono", rowRecibe[4].toString().trim());
                        infCtaRecibe.put("codigoCliente", rowRecibe[5].toString().trim());

                        response.put("informacionCtaRecibe", infCtaRecibe);
                    }
                    Libs fechaHoraService = new Libs(entityManager);
                    String FechaHora = fechaHoraService.obtenerFechaYHora();

                    String numTransfer = String.valueOf(returnValue);
                    String valTransf = String.valueOf(valTransferencia);
                     PassSecure passSecure = new PassSecure();
                    String infoConcatena = numSocio +FechaHora;
                    String encrip2 =  passSecure.encryptPassword(infoConcatena);
                    String encrip1 = encrip2.substring(0,10);
                    String sqlInsertTravir =
                            "INSERT INTO andtravir (travir_cod_socio, travir_cta_desti, travir_val_trans, travir_fec_trans, travir_num_ttran, travir_cod_encri1, travir_cod_encri2) " +
                                    "VALUES (:travir_cod_socio, :travir_cta_desti, :travir_val_trans, CURRENT, :travir_num_ttran, :travir_cod_encri1, :travir_cod_encri2)";

                    Query resultInsertTravir = entityManager.createNativeQuery(sqlInsertTravir);
                    resultInsertTravir.setParameter("travir_cod_socio", numSocio);
                    resultInsertTravir.setParameter("travir_cta_desti", ctadpCodCtadpDestino);
                    resultInsertTravir.setParameter("travir_val_trans", valTransferencia);
                    resultInsertTravir.setParameter("travir_num_ttran", returnValue);
                    resultInsertTravir.setParameter("travir_cod_encri1", encrip1);
                    resultInsertTravir.setParameter("travir_cod_encri2", encrip2);

                    resultInsertTravir.executeUpdate();
                    String sqlUpdatesToken =
                            "UPDATE vircodaccess " +
                                    "SET codaccess_estado = :estado_up " +
                                    "WHERE codaccess_cedula = :cedula " +
                                    "AND codaccess_usuario = :usuario " +
                                    "AND codsms_codigo = :codsms " +
                                    "AND codaccess_estado = :estado";

                    Query queryUpdatesToken = entityManager.createNativeQuery(sqlUpdatesToken);
                    queryUpdatesToken.setParameter("estado_up", 0);
                    queryUpdatesToken.setParameter("codsms", 9);
                    queryUpdatesToken.setParameter("cedula", clienIdenti);
                    queryUpdatesToken.setParameter("usuario", cliacUsuRuc);
                    queryUpdatesToken.setParameter("estado", 1);

                    int rowsUpdatesd = queryUpdatesToken.executeUpdate();

                    if (rowsUpdatesd == 0) {
                        response.put("success", false);
                        response.put("message", "No se pudo actualizar el estado del código temporal");
                        response.put("status", "AA024");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }

                    intentosRealizadoTokenFallos = 0;
                    response.put("message", "TRANSFERENCIA REALIZADA CON ÉXITO :)" );
                    response.put("numTransferencia", returnValue);
                    response.put("status", "DTROK0005");
                    response.put("success", true);

                }

                return new ResponseEntity<>(response, HttpStatus.OK);

            }else{
                response.put("message", "MONTO INSUFICIENTE PARA REALIZAR LA TRANSFERENCIA ");
                response.put("success", false);
                response.put("error", "ERROR005");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Error interno del servidor");
            response.put("status", false);
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, Object>> genCodDirectas(HttpServletRequest request, Authentication authentication, TransfDirectUtils dto) {
        try {
            Map<String, Object> response = new HashMap<>();
            List<Map<String, Object>> allDataList = new ArrayList<>();

            String token = Obtenertoken.desdeCookie(request);


            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String cliacUsuRuc = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            String numeroCuentaEnvio = dto.getCtaEnvio();
            String numeroCtaDestino = dto.getCtaDestino();

            // Validación de datos del token
            if (cliacUsuRuc == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos");
                response.put("status", "AA34050");
                response.put("error", "ERROR EN LA AUTENTICACIÓN");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            // Validación de cuenta origen
            if (numeroCuentaEnvio == null || !numeroCuentaEnvio.matches("\\d{12}")) {
                response.put("message", "El número de cuenta origen debe tener exactamente 12 dígitos numéricos.");
                response.put("status", "ERROR763309");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            // Validación de cuenta destino
            if (numeroCtaDestino == null || !numeroCtaDestino.matches("\\d{12}")) {
                response.put("message", "El número de cuenta destino debe tener exactamente 12 dígitos numéricos.");
                response.put("status", "ERROR58023");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            Libs fechaHoraService = new Libs(entityManager);
            String fecha = fechaHoraService.obtenerFechaYHora();

            String sqlQueryOrigen = "SELECT clien_cod_empre, clien_cod_ofici, ctadp_cod_ctadp, usvco_tlf_usvco, usvco_ema_usvco, clien_nom_clien, clien_ape_clien " +
                    "FROM cnxctadp, cnxclien, andusvco " +
                    "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                    "AND ctadp_cod_ectad = :ctadp_cod_ectad " +
                    "AND ctadp_cod_clien = :clien_cod_clien " +
                    "AND clien_ide_clien = :clien_ide_clien " +
                    "AND ctadp_cod_clien = clien_cod_clien " +
                    "AND usvco_ide_clien = clien_ide_clien " +
                    "AND usvco_tip_usvco = '1' ";

            // Consulta cuenta origen
            Query query = entityManager.createNativeQuery(sqlQueryOrigen);
            query.setParameter("ctadp_cod_ctadp", numeroCuentaEnvio);
            query.setParameter("ctadp_cod_ectad", "1");
            query.setParameter("clien_cod_clien", numSocio);
            query.setParameter("clien_ide_clien",clienIdenti);
            List<Object[]> results = query.getResultList();

            String sqlQueryVerDestino = """
                    SELECT * FROM cnxctadp WHERE ctadp_cod_ctadp = :cta_banco AND ctadp_cod_ectad = '1'
                    """;

            // Consulta cuenta destino
            Query query1 = entityManager.createNativeQuery(sqlQueryVerDestino);
            query1.setParameter("cta_banco", numeroCtaDestino);
            List<Object[]> results1 = query1.getResultList();

            // Procesar resultados cuenta origen
            if (results.isEmpty()) {
                response.put("message", "Cuenta origen no encontrada, bloqueda o cerrada");
                response.put("status", "ERROR42037");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Procesar resultados cuenta destino
            if (results1.isEmpty()) {
                response.put("message", "Cuenta destino no encontrada, bloqueda o cerrada");
                response.put("status", "ERROR962037");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Extraer datos de las cuentas
            Object[] resultEnvio = results.get(0);

            String tlfCtaEnvio = resultEnvio[3].toString().trim();
            String emailCtaEnvio = resultEnvio[4].toString().trim();
            String nombreCtaEnvio = resultEnvio[5].toString().trim();
            String apellCtaEnvio = resultEnvio[6].toString().trim();

            //generar codigo
            String CodigoTrfDirectas = codigoAleatorio6Temp();
            SendSMS smsDesbloqueo = new SendSMS();
            smsDesbloqueo.sendSecurityCodeSMS(tlfCtaEnvio,"1150",CodigoTrfDirectas,"efectuar la Transferencia directa", fecha);
            // Enviar correo
            sendEmail enviarCorreo = new sendEmail();
            enviarCorreo.sendEmailTokenTemp(apellCtaEnvio, nombreCtaEnvio, fecha, emailCtaEnvio, CodigoTrfDirectas);

            // Actualizar estados anteriores a 0
            String sqlUpdateEstado = "UPDATE vircodaccess SET codaccess_estado = '0' WHERE codaccess_cedula = :codaccess_cedula AND codaccess_usuario = :codaccess_usuario AND codaccess_estado = '1' AND codsms_codigo = 9";
            Query resultUpdateEstado = entityManager.createNativeQuery(sqlUpdateEstado);
            resultUpdateEstado.setParameter("codaccess_cedula", clienIdenti);
            resultUpdateEstado.setParameter("codaccess_usuario", cliacUsuRuc);
            resultUpdateEstado.executeUpdate();

            // Insertar nuevo código temporal

            String sqlInsertToken = "INSERT INTO vircodaccess (codaccess_cedula, codaccess_usuario, codaccess_codigo_temporal, codsms_codigo, codaccess_estado, codaccess_fecha) " +
                    "VALUES (:codaccess_cedula, :codaccess_usuario, :codaccess_codigo_temporal, :codsms_codigo, :codaccess_estado, :codaccess_fecha)";
            Query resultInsertTokenAcceso = entityManager.createNativeQuery(sqlInsertToken);
            resultInsertTokenAcceso.setParameter("codaccess_cedula", clienIdenti);
            resultInsertTokenAcceso.setParameter("codaccess_usuario", cliacUsuRuc);
            resultInsertTokenAcceso.setParameter("codaccess_codigo_temporal", CodigoTrfDirectas);
            resultInsertTokenAcceso.setParameter("codsms_codigo", 9);
            resultInsertTokenAcceso.setParameter("codaccess_estado", "1");
            resultInsertTokenAcceso.setParameter("codaccess_fecha", fecha);
            resultInsertTokenAcceso.executeUpdate();
            tokenExpirationService.programarExpiracionToken(clienIdenti, CodigoTrfDirectas, "9");

            response.put("message", "CODIGO GENERADO CON EXITO ");
            response.put("status", "CODTRFOK005");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Error interno del servidor");
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String obtenerSaldoDisponible(String txtcodctadp) throws Exception {
        try {
            Libs fechaHoraService = new Libs(entityManager);
            String fecha = fechaHoraService.obtenerFecha();
            System.out.println(fecha);
            String sqlSaldoDisponible = "CALL cnxprc_sldos_ctadp(:codigoCuenta, :fecha)";
            Query querySaldo = entityManager.createNativeQuery(sqlSaldoDisponible);
            querySaldo.setParameter("codigoCuenta", txtcodctadp);
            querySaldo.setParameter("fecha", fecha);
            List<Object[]> resultadoSaldo = querySaldo.getResultList();
            if (resultadoSaldo.isEmpty()) {
                throw new Exception("No se pudo obtener el saldo disponible.");
            }
            return resultadoSaldo.get(0)[0].toString().trim();
        } catch (Exception e) {
            throw new Exception("Error al obtener el saldo disponible: " + e.getMessage(), e);
        }
    }
    public String codigoAleatorio6Temp() {
        // Genera un número aleatorio de 6 dígitos
        Random random = new Random();
        int numeroAleatorio = 100000 + random.nextInt(900000);
        return String.valueOf(numeroAleatorio);
    }
}
