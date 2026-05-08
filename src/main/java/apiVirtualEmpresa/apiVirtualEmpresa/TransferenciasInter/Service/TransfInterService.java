package apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasInter.Service;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.TokenExpirationService;
import apiVirtualEmpresa.apiVirtualEmpresa.TransferenciasInter.dto.TransfInterUtils;
import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import sms.SendSMS;
import envioCorreo.sendEmail;
import apiVirtualEmpresas.virtualempresas.libs.Libs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service
@Transactional


public class TransfInterService {
    @Autowired
    private TokenExpirationService tokenExpirationService;

    @PersistenceContext
    private EntityManager entityManager;

    private final JwtUtil jwtUtil;

    public TransfInterService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Autowired
    private PlatformTransactionManager transactionManager;
    int intentosRealizadoTokenFallos = 0;
    int intentosRealizadoTokenFallosInterban = 0;

    // LISTA DE CUENTAS TRANSFERIBLES

    public ResponseEntity<Map<String, Object>> lisCtaTransferibles(HttpServletRequest request,Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {

            // 1. TOKEN DESDE COOKIE

            String token = Obtenertoken.desdeCookie(request);

            if (token == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA027");
                err.put("errors", "No autorizado: no fue posible obtener el token.");
                allDataList.add(err);

                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            // 2. VALIDAR AUTENTICACIÓN

            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            // 3. LEER DATOS DEL TOKEN

            String cliacUsuRuc = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);
            if (cliacUsuRuc == null || clienIdenti == null || numSocio == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER001");
                err.put("errors", "Datos del token incompletos.");
                allDataList.add(err);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            // 4. SQL LISTA DE CUENTAS TRANSFERIBLES

            String sql =
                    "SELECT " +
                            "ctadp_cod_ctadp, " +
                            "ctadp_cod_depos, " +
                            "depos_des_depos, " +
                            "ctadp_cod_ectad, " +
                            "clien_nom_clien, " +
                            "clien_ape_clien, " +
                            "ctadp_sal_dispo " +
                            "FROM cnxclien, cnxctadp, cnxdepos, cnxopdep " +
                            "WHERE clien_ide_clien = :clienIdenti " +
                            "AND ctadp_cod_empre = clien_cod_empre " +
                            "AND ctadp_cod_ofici = clien_cod_ofici " +
                            "AND ctadp_cod_clien = clien_cod_clien " +
                            "AND ctadp_cod_depos IN (1,9) " +
                            "AND depos_cod_empre = ctadp_cod_empre " +
                            "AND depos_cod_ofici = ctadp_cod_ofici " +
                            "AND depos_cod_depos = ctadp_cod_depos " +
                            "AND depos_ctr_opera = 0 " +
                            "AND depos_cod_moned = 2 " +
                            "AND opdep_cod_empre = ctadp_cod_empre " +
                            "AND opdep_cod_ofici = ctadp_cod_ofici " +
                            "AND opdep_cod_depos = ctadp_cod_depos " +
                            "AND opdep_cod_ectad = ctadp_cod_ectad " +
                            "AND opdep_cod_toper = '3' " +
                            "ORDER BY ctadp_cod_depos";

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("clienIdenti", clienIdenti);

            List<Object[]> results = query.getResultList();

            if (results.isEmpty()) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER002");
                err.put("errors", "No posee cuentas disponibles para transferir.");
                allDataList.add(err);

                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // 5. ARMAR RESPUESTA

            Libs fechaHoraService = new Libs(entityManager);
            String fecha = fechaHoraService.obtenerFechaYHora();
            int i = 1;
            for (Object[] row : results) {
                Map<String, Object> cuenta = new HashMap<>();
                cuenta.put("fecha", fecha);
                cuenta.put("numeroCta", i++);
                cuenta.put("numeroCuenta", row[0].toString().trim());
                cuenta.put("codigoDeposito", row[1].toString().trim());
                cuenta.put("descrCta", row[2].toString().trim());
                cuenta.put("estadCta", row[3].toString().trim());
                cuenta.put("nombre", row[4].toString().trim());
                cuenta.put("apellido",  row[5].toString().trim());
                double saldo = row[6] != null ? Double.parseDouble(row[6].toString()) : 0.0;
                cuenta.put("saldoCta", saldo);
                allDataList.add(cuenta);
            }

            response.put("success", true);
            response.put("CuentasTransferibles", allDataList);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {

            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERRORTRFINTER500");
            err.put("errors", e.getMessage());

            List<Map<String, Object>> errList = new ArrayList<>();
            errList.add(err);

            response.put("AllData", errList);
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // LISTA DE INSTITUTCIONES INTERBANCARIAS

    public ResponseEntity<Map<String, Object>> listarInstFinancieras(HttpServletRequest request, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        Map<String,Object>allData = new HashMap<>();
        try {

            String token = Obtenertoken.desdeCookie(request);

            String cliacUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);


            if (cliacUsuVirtu == null || clienIdenti == null || numSocio == null) {
                List<Map<String, Object>> allDataList =  new ArrayList<>();
                allData.put("message", "Datos del token incompletos");
                allData.put("status", "ERRORTRFINTER001");
                allData.put("errors", "ERROR EN LA AUTENTICACIÓN");
                allDataList.add(allData);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String sqlInstiFinancieras =
                    "SELECT ifspi_cod_ifspi, ifspi_nom_ifspi FROM cnxifspi WHERE ifspi_bce_ctaco IS NOT NULL " +
                            "AND length(ifspi_bce_ctaco) > 0 " +
                            "AND ifspi_cod_ifspi NOT IN (3) ORDER BY ifspi_cod_ifspi";
            Query queryIntituFinan = entityManager.createNativeQuery(sqlInstiFinancieras);
            List<Object[]> resultados = queryIntituFinan.getResultList();
            if (resultados.isEmpty()) {
                response.put("message", "No se encontrar instituciciones financieras en la BDD.");
                response.put("status", "ERROR001");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            List<Map<String, Object>> institucionesList = new ArrayList<>();
            for (Object[] row : resultados) {
                Map<String, Object> institucion = new HashMap<>();
                institucion.put("codigo", row[0].toString().trim());
                institucion.put("nombreInstitucion", row[1].toString().trim());
                institucionesList.add(institucion);
            }
            response.put("Instituciones", institucionesList);
            response.put("status", "INFOUSEROK");
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error interno del servidor");
            errorResponse.put("status", "ERROR001");
            errorResponse.put("errors", e.getMessage());
            return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //código temporal pra transferencias interbancarias


    public ResponseEntity<Map<String, Object>> genCodInterbancarias(HttpServletRequest request, Authentication authentication, TransfInterUtils dto) {

        try {


            String token = Obtenertoken.desdeCookie(request);

            String  rucUsuVirtu  = authentication.getName();
            String  clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            Map<String, Object> response = new HashMap<>();
            String numeroCuentaEnvio = dto.getCtaEnvio();
            String numeroCtaDestino = dto.getCtaDestino();

            // Validación de datos del token
            if (rucUsuVirtu == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos "+rucUsuVirtu+" : "+clienIdenti);
                response.put("status", "AA1767");
                response.put("error", "ERROR EN LA AUTENTICACIÓN");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            // Validación de cuenta origen
            if (numeroCuentaEnvio == null || !numeroCuentaEnvio.matches("\\d{12}")) {
                response.put("message", "El número de cuenta origen debe tener exactamente 12 dígitos numéricos."+numeroCuentaEnvio);
                response.put("status", "ERROR3032");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (numeroCtaDestino == null || !numeroCtaDestino.matches("\\d+")) {
                response.put("message", "El número de cuenta destino solo debe contener caracteres numéricos."+numeroCtaDestino);
                response.put("status", "ERROR1492");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            Libs fechaHoraService = new Libs(entityManager);
            String fecha = fechaHoraService.obtenerFechaYHora();

            String sqlQuery = "SELECT FIRST 1 clien_cod_empre, clien_cod_ofici, ctadp_cod_ctadp, usvco_tlf_usvco, usvco_ema_usvco, clien_nom_clien, clien_ape_clien " +
                    "FROM cnxctadp, cnxclien, andusvco " +
                    "WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp " +
                    "AND ctadp_cod_depos IN (1,9) " +
                    "AND ctadp_cod_ectad = :ctadp_cod_ectad " +
                    "AND ctadp_cod_clien = clien_cod_clien " +
                    "AND clien_ide_clien = usvco_ide_clien " +
                    "AND usvco_tip_usvco = '1'";


            // Consulta cuenta origen
            Query query = entityManager.createNativeQuery(sqlQuery);
            query.setParameter("ctadp_cod_ctadp", numeroCuentaEnvio);
            query.setParameter("ctadp_cod_ectad", "1");
            List<Object[]> results = query.getResultList();



            // Procesar resultados cuenta origen
            if (results.isEmpty()) {
                response.put("message", "Cuenta origen no encontrada o inválida");
                response.put("status", "ERROR7780");
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
            smsDesbloqueo.sendSecurityCodeSMS(tlfCtaEnvio,"1150",CodigoTrfDirectas,"efectuar la Transferencia interbancaria", fecha);
            // Enviar correo
            sendEmail enviarCorreo = new sendEmail();
            enviarCorreo.sendEmailTokenTemp(apellCtaEnvio, nombreCtaEnvio, fecha, emailCtaEnvio, CodigoTrfDirectas);



            String sqlBloqUser = "UPDATE vircodaccess SET codaccess_estado = '0' WHERE codaccess_cedula = :rudIdenClie AND codaccess_usuario = :ideClieUsu AND codaccess_estado = '1' AND codsms_codigo = 10";
            Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
            resultBloqUser.setParameter("rudIdenClie", clienIdenti);
            resultBloqUser.setParameter("ideClieUsu", rucUsuVirtu);
            resultBloqUser.executeUpdate();

            // Insertar nuevo código temporal
            String sqlInsertAccesos ="INSERT INTO vircodaccess "
                            + "(codaccess_cedula, codaccess_usuario, codaccess_codigo_temporal, codsms_codigo, codaccess_estado, codaccess_fecha) "
                            + "VALUES (:codaccess_cedula, :codaccess_usuario, :codaccess_codigo_temporal, :codsms_codigo, :codaccess_estado, :codaccess_fecha)";

            Query resultInsertAcceso = entityManager.createNativeQuery(sqlInsertAccesos);
            resultInsertAcceso.setParameter("codaccess_cedula", clienIdenti);
            resultInsertAcceso.setParameter("codaccess_usuario", rucUsuVirtu);
            resultInsertAcceso.setParameter("codaccess_codigo_temporal", CodigoTrfDirectas);
            resultInsertAcceso.setParameter("codsms_codigo", "10");
            resultInsertAcceso.setParameter("codaccess_estado", "1");
            resultInsertAcceso.setParameter("codaccess_fecha", fecha);
            resultInsertAcceso.executeUpdate();
            tokenExpirationService.programarExpiracionToken(clienIdenti, CodigoTrfDirectas, "10");
            response.put("message", "CODIGO GENERADO CON EXITO " );
            response.put("status", "CODTRFOK005");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Error interno del servidor");
            response.put("status", "ERROR8282");
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    //grabrar interbancarias
    public ResponseEntity<Map<String, Object>> srtGrabarInterban(HttpServletRequest request, Authentication authentication, TransfInterUtils dto) {
        // Configuración de la transacción
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("TransferenciaTransaction");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            String token = Obtenertoken.desdeCookie(request);

            String rucUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);



            Map<String, Object> response = new HashMap<>();

            // Validación de datos del token
            if (rucUsuVirtu == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos");
                response.put("status", "AA7294");
                response.put("error", "ERROR EN LA AUTENTICACIÓN");
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
            queryBloqueo.setParameter("ideUsu", rucUsuVirtu);

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
                response.put("status", "ERROR3752");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            if (numeroCtaDestino == null || !numeroCtaDestino.matches("\\d+")) {
                response.put("message", "El número de cuenta destino debe contener únicamente dígitos numéricos.");
                response.put("status", "ERROR1813");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            if (dto.getCodTempTransDirec() == null || !dto.getCodTempTransDirec().matches("\\d{6}")) {
                response.put("message", "Código de seguridad inválido");
                response.put("status", "AA9297");
                response.put("error", "El código debe contener exactamente 6 dígitos");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            if (descripcionTrf == null || descripcionTrf.trim().isEmpty() || descripcionTrf.length() > 250) {
                response.put("message", "La descripción de la transferencia no puede estar vacía y debe tener como máximo 250 caracteres.");
                response.put("status", "ERROR1210");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            if (valTransferencia == null || valTransferencia <= 0 || !valTransferencia.toString().matches("^\\d{1,14}(\\.\\d{1,2})?$")) {
                response.put("message", "El monto de la transferencia debe ser un número positivo con hasta 14 dígitos enteros y 2 decimales.");
                response.put("status", "ERROR4073");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            //Obtener informacion para destino interbancario

            String clieIdBancoRecibe = dto.getTipCodInsti().trim();
            String titulaCtaRecibe = dto.getNombresBeneficiario().trim();
            String cedulaCtaRecibe = dto.getCedulaBeneficiario().trim();
            Integer tipoctabce = dto.getTipoctabce();
            // Validación clieIdBancoRecibe (solo números enteros, obligatorio)
            if (clieIdBancoRecibe == null || clieIdBancoRecibe.trim().isEmpty() || !clieIdBancoRecibe.matches("^\\d+$")) {
                response.put("message", "El código del banco receptor es obligatorio y debe contener únicamente números enteros.");
                response.put("status", "ERROR6541");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            // Validación titulaCtaRecibe (nombre beneficiario, solo letras y espacios, máx. 100)
            if (titulaCtaRecibe == null || titulaCtaRecibe.trim().isEmpty() || titulaCtaRecibe.length() > 100 || !titulaCtaRecibe.matches("^[a-zA-ZÁÉÍÓÚÑáéíóúñ\\s]+$")) {
                response.put("message", "El nombre del titular no puede estar vacío, debe contener solo letras y tener como máximo 100 caracteres.");
                response.put("status", "ERROR7626");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Validación cedulaCtaRecibe (cédula/RUC, 10 o 13 dígitos)
            if (cedulaCtaRecibe == null || cedulaCtaRecibe.trim().isEmpty() || !cedulaCtaRecibe.matches("^\\d{10}(\\d{3})?$")) {
                response.put("message", "La cédula/RUC del beneficiario debe contener 10 o 13 dígitos numéricos válidos.");
                response.put("status", "ERROR4002");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            // Validación tipoctabce (tipo de cuenta, 1 = Ahorros, 2 = Corriente)
            if (tipoctabce == null || (tipoctabce != 1 && tipoctabce != 2)) {
                response.put("message", "El tipo de cuenta es obligatorio y debe ser 1 (Ahorros) o 2 (Corriente).");
                response.put("status", "ERROR2845");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            String sqlVerificaTokenBDD = "SELECT codaccess_codigo_temporal FROM vircodaccess " +
                    "WHERE codaccess_cedula = :codaccess_cedula AND codaccess_usuario = :codaccess_usuario " +
                    "AND codaccess_estado = :codaccess_estado AND codsms_codigo = '10' ";
            Query queryVerificaTokenBDD = entityManager.createNativeQuery(sqlVerificaTokenBDD);
            queryVerificaTokenBDD.setParameter("codaccess_cedula", clienIdenti);
            queryVerificaTokenBDD.setParameter("codaccess_usuario", rucUsuVirtu);
            queryVerificaTokenBDD.setParameter("codaccess_estado", "1");

            List<Object[]> resultsTokenBDD = queryVerificaTokenBDD.getResultList();


            if (resultsTokenBDD.isEmpty()) {
                response.put("message", "CODIGO TEMPORAL EXPIRADO, POR EXCEDER LOS 4 MINUTOS");
                response.put("status", "AA1879");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            String saldoDisponible = obtenerSaldoDisponible(numeroCuentaEnvio);
            System.out.println(saldoDisponible);
            Float saldoDispoParse = Float.parseFloat(saldoDisponible);


            String tokenFromDB = (String) queryVerificaTokenBDD.getSingleResult();
            if (!tokenFromDB.trim().equals(dto.getCodTempTransDirec())) {
                intentosRealizadoTokenFallosInterban++;
                if (intentosRealizadoTokenFallosInterban >= 3) {
                    // Bloquear usuario
                    String sqlBloqUser = "UPDATE andusvco SET usvco_ctr_bloq = :bloqueo WHERE usvco_ide_clien = :rudIdenClie AND usvco_ide_usvco = :ideClieUsu";
                    Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                    resultBloqUser.setParameter("bloqueo", "0");
                    resultBloqUser.setParameter("rudIdenClie", clienIdenti);
                    resultBloqUser.setParameter("ideClieUsu", rucUsuVirtu);

                    try {
                        int rowsUpdated = resultBloqUser.executeUpdate();
                        if (rowsUpdated > 0) {

                            // Obtener datos para el correo
                            String sqlDatosCorreoIngreso = "SELECT usvco_nom_usvco, usvco_ema_usvco FROM andusvco WHERE usvco_ide_clien = :usvco_ide_clien AND usvco_ide_usvco = :usvco_ide_usvco";
                            Query resulDatosCorreoIngreso = entityManager.createNativeQuery(sqlDatosCorreoIngreso);
                            resulDatosCorreoIngreso.setParameter("usvco_ide_clien", rucUsuVirtu);
                            resulDatosCorreoIngreso.setParameter("usvco_ide_usvco", clienIdenti);
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
                            queryUpdatesToken.setParameter("codsms", 10);
                            queryUpdatesToken.setParameter("cedula", clienIdenti);
                            queryUpdatesToken.setParameter("usuario", rucUsuVirtu);
                            queryUpdatesToken.setParameter("estado", 1);

                            int rowsUpdatesd = queryUpdatesToken.executeUpdate();

                            if (rowsUpdatesd == 0) {
                                response.put("success", false);
                                response.put("message", "No se pudo actualizar el estado del código temporal");
                                response.put("status", "AA024");
                                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                            }

                            intentosRealizadoTokenFallosInterban = 0;
                            response.put("message", "Usuario bloqueado por exceder límite de intentos");
                            response.put("status", "AA5059");
                            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                        }
                    } catch (Exception e) {

                        response.put("message", "Error al intentar bloquear el usuario");
                        response.put("status", "AA4823");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }
                } else {
                    response.put("message", "Código temporal incorrecto. Intentos restantes: " + (3 - intentosRealizadoTokenFallosInterban));
                    response.put("status", "AA05478");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
            }
            double valorsumado = Math.round((0.36 + valTransferencia) * 100.0) / 100.0;

            if (saldoDispoParse >= valorsumado){
                String sqlQuery = """
                SELECT ctadp_cod_empre, ctadp_cod_ofici, clien_ape_clien, clien_nom_clien, ctadp_cod_clien, clien_ide_clien 
                FROM cnxctadp, cnxclien 
                WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp AND ctadp_cod_clien = :ctadp_cod_clien 
                AND ctadp_cod_ectad = '1' 
                AND ctadp_cod_clien = clien_cod_clien
                        """;

                // Consulta cuenta origen
                Query query = entityManager.createNativeQuery(sqlQuery);
                query.setParameter("ctadp_cod_ctadp", numeroCuentaEnvio);
                query.setParameter("ctadp_cod_clien", numSocio);
                List<Object[]> results = query.getResultList();


                // Procesar resultados cuenta origen
                if (results.isEmpty()) {
                    response.put("message", "Cuenta origen no encontrada, no activa o no pertenece al socio perteneciente a esta cuenta!");
                    response.put("status", "ERROR8017");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }


                // Extraer datos de las cuentas
                Object[] resultEnvio = results.get(0);


                String clieCodEmpresaEnvio = resultEnvio[0].toString().trim();
                String clienCodOficiEnvio = resultEnvio[1].toString().trim();
                String clienApellEnvio = resultEnvio[2].toString().trim();
                String clieNomEnvio = resultEnvio[3].toString().trim();
                String clienCodEnvio = resultEnvio[4].toString().trim();
                String clinIdenEnvio = resultEnvio[5].toString().trim();
                String nomApellido = clienApellEnvio +" " +clieNomEnvio;



                String callTransferProcedure = "CALL cnxprc_reg_spi01_wb(:clienCodEmpreEnvio, :clienCodOficiEnvio,'803',:clienCodEmpreEnvio," +
                        ":clienCodOficiEnvio,:clienCodEnvio," +
                        ":clinIdenEnvio, :nomApellido,:numeroCuentaEnvio,:valTransferencia," +
                        ":cedulaCtaRecibe,:titulaCtaRecibe," +
                        ":clieIdBancoRecibe,:numeroCtaDestino,:tipoctabce,'TRANSFERENCIAS INTERBANCARIAS EN LINEA',1,'0.36')";
                Query queryProcedure = entityManager.createNativeQuery(callTransferProcedure);

                queryProcedure.setParameter("clienCodEmpreEnvio", clieCodEmpresaEnvio);
                queryProcedure.setParameter("clienCodOficiEnvio", clienCodOficiEnvio);
                queryProcedure.setParameter("clienCodEnvio",clienCodEnvio);
                queryProcedure.setParameter("clinIdenEnvio", clinIdenEnvio);
                queryProcedure.setParameter("nomApellido", nomApellido);
                queryProcedure.setParameter("numeroCuentaEnvio", numeroCuentaEnvio);
                queryProcedure.setParameter("valTransferencia", valTransferencia);
                queryProcedure.setParameter("cedulaCtaRecibe",cedulaCtaRecibe);
                queryProcedure.setParameter("titulaCtaRecibe",titulaCtaRecibe);
                queryProcedure.setParameter("clieIdBancoRecibe",clieIdBancoRecibe);
                queryProcedure.setParameter("numeroCtaDestino",numeroCtaDestino);
                queryProcedure.setParameter("tipoctabce",tipoctabce);
                Object result = queryProcedure.getSingleResult();
                int returnValue = Integer.parseInt(result.toString());
                double valComision = 0.36;
                ResponseEntity<Map<String, Object>> grabar2Response = grabar2(
                        clieCodEmpresaEnvio,
                        clienCodOficiEnvio,
                        clinIdenEnvio,
                        "0",
                        "803",
                        valComision,
                        1,
                        nomApellido,
                        "0",
                        "0",
                        numeroCuentaEnvio,
                        15,
                        "125"
                );
                if (grabar2Response.getStatusCode() == HttpStatus.OK) {

                    String sqlUpdatesToken =
                            "UPDATE vircodaccess " +
                                    "SET codaccess_estado = :estado_up " +
                                    "WHERE codaccess_cedula = :cedula " +
                                    "AND codaccess_usuario = :usuario " +
                                    "AND codsms_codigo = :codsms " +
                                    "AND codaccess_estado = :estado";

                    Query queryUpdatesToken = entityManager.createNativeQuery(sqlUpdatesToken);
                    queryUpdatesToken.setParameter("estado_up", 0);
                    queryUpdatesToken.setParameter("codsms", 10);
                    queryUpdatesToken.setParameter("cedula", clienIdenti);
                    queryUpdatesToken.setParameter("usuario", rucUsuVirtu);
                    queryUpdatesToken.setParameter("estado", 1);

                    int rowsUpdatesd = queryUpdatesToken.executeUpdate();

                    if (rowsUpdatesd == 0) {
                        response.put("success", false);
                        response.put("message", "No se pudo actualizar el estado del código temporal");
                        response.put("status", "AA024");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }


                    intentosRealizadoTokenFallosInterban = 0;
                    response.put("message", "TRANSFERENCIA INTERBANCARIA REALIZADA CON ÉXITO !!");
                    response.put("numTransferencia", returnValue);
                    response.put("status", "DTROK0005");
                    transactionManager.commit(status);
                    return new ResponseEntity<>(response, HttpStatus.OK);
                } else {
                    return grabar2Response;
                }
            }else{
                transactionManager.rollback(status);
                response.put("message", "MONTO INSUFICIENTE PARA REALIZAR LA TRANSFERENCIA ");
                response.put("error", "ERROR105");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            transactionManager.rollback(status);
            response.put("message", "Error interno del servidor");
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, Object>> grabar2(String codigoEmpresa, String codigoOficina, String cedula, String codigoOperador,
                                                       String txtcaja, Double valunida, Integer cantidad, String beneficiario,
                                                       String codGcomic, String codComic, String txtcuenta, Integer servicio, String numTrans) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Validar servicio
            if (servicio != 15 && servicio != 16) {
                response.put("message", "Servicio no válido");
                response.put("status", "ERROR001");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Consultar servicio
            String sqlComic = "SELECT tpser_cod_tpser FROM andtpser WHERE tpser_cod_tpser = :servicio AND tpser_estado_tpser = 1";
            Query queryComic = entityManager.createNativeQuery(sqlComic);
            queryComic.setParameter("servicio", servicio);
            List<?> rsComic = queryComic.getResultList();

            if (rsComic.isEmpty()) {
                response.put("message", "Servicio no activo");
                response.put("status", "ERROR002");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Determinar si es socio
            String sqlSocioCliente = "SELECT clien_ctr_socio FROM cnxclien WHERE clien_ide_clien = :cedula AND clien_ctr_socio = 1 AND clien_ctr_estad IN (1, 2)";
            Query querySocioCliente = entityManager.createNativeQuery(sqlSocioCliente);
            querySocioCliente.setParameter("cedula", cedula);
            List<?> rsSocioCliente = querySocioCliente.getResultList();

            int codfprod = 15; // COMISIONES SERVICIOS CON IVA si no es socio
            int iva = 2;

            if (!rsSocioCliente.isEmpty()) {
                codfprod = 16; // COMISIONES SERVICIOS SIN IVA si es socio
                iva = 1;
            }

            // Calcular IVA
            String sqlIva = "SELECT impts_cod_impts, impts_cod_imsri, impts_por_impts, timpt_cod_tmsri " +
                    "FROM eceimpts, ecetimpt " +
                    "WHERE impts_ctr_habil = 1 " +
                    "AND timpt_cod_timpt = impts_cod_timpt " +
                    "AND impts_cod_impts = :iva " +
                    "ORDER BY impts_cod_impts";
            Query queryIva = entityManager.createNativeQuery(sqlIva);
            queryIva.setParameter("iva", iva);
            List<Object[]> rsIva = queryIva.getResultList();

            BigDecimal tarifa = BigDecimal.ZERO;
            if (!rsIva.isEmpty()) {
                tarifa = new BigDecimal(rsIva.get(0)[2].toString());
            }
            System.err.println(tarifa);
            float ivaCalculado = (valunida.floatValue() * cantidad.floatValue() * tarifa.floatValue()) / 100;
            ivaCalculado = redondearMoneda(ivaCalculado);
            System.err.println(ivaCalculado);

            // Obtener datos del cliente
            String sqlCli = "SELECT TRIM(clien_ape_clien) || ' ' || TRIM(clien_nom_clien) AS cliente, clien_dir_email AS email " +
                    "FROM cnxclien WHERE clien_ide_clien = :cedula";
            Query queryCli = entityManager.createNativeQuery(sqlCli);
            queryCli.setParameter("cedula", cedula);
            List<Object[]> rsCli = queryCli.getResultList();

            String email = "";
            if (!rsCli.isEmpty()) {
                beneficiario = eliminarAcentos(rsCli.get(0)[0].toString());
                email = rsCli.get(0)[1].toString();
            } else {
                String sqlCliente = "SELECT rclie_raz_apenm, rclie_dir_email FROM ecerclie WHERE rclie_ide_rclie = :cedula";
                Query queryCliente = entityManager.createNativeQuery(sqlCliente);
                queryCliente.setParameter("cedula", cedula);
                List<Object[]> rsCliente = queryCliente.getResultList();

                if (!rsCliente.isEmpty()) {
                    beneficiario = eliminarAcentos(rsCliente.get(0)[0].toString());
                    email = rsCliente.get(0)[1].toString();
                }
            }

            // Calcular subtotal y total factura
            float subtotal = valunida.floatValue() * cantidad.floatValue();
            subtotal = redondearMoneda(subtotal);
            float totalFactura = subtotal + ivaCalculado;
            totalFactura = redondearMoneda(totalFactura);
            // Procesar IVA para servicios específicos
            if (servicio == 15 || servicio == 16) {

                String cciva = "25040595";
                String desiva = "";

                String sqlIvaProf = "SELECT profi_val_carac as cciva, profi_des_profi as desiva " +
                        "FROM cnxprofi WHERE profi_cod_profi = 'dmnsefectc' AND profi_cod_ofici = :oficina";
                Query queryIvaProf = entityManager.createNativeQuery(sqlIvaProf);
                queryIvaProf.setParameter("oficina", codigoOficina);
                List<Object[]> rsIvaProf = queryIvaProf.getResultList();

                if (!rsIvaProf.isEmpty()) {
                    cciva = rsIvaProf.get(0)[0].toString().trim();
                    desiva = eliminarAcentos(rsIvaProf.get(0)[1].toString());
                }
                System.err.println(ivaCalculado);
                // Debito y contable
                if (ivaCalculado > 0) {
                    String callRegNddct = "CALL andsp_reg_nddct_iva(:codEmpresa, :codOficina, :txtcaja, :desiva, " +
                            ":txtcuenta, '', :cciva, :iva, '', 0, '', 0, '', 0, '', 0, :iva, 1, :numTrans)";
                    Query queryRegNddct = entityManager.createNativeQuery(callRegNddct);
                    queryRegNddct.setParameter("codEmpresa", codigoEmpresa);
                    queryRegNddct.setParameter("codOficina", codigoOficina);
                    queryRegNddct.setParameter("txtcaja", txtcaja);
                    queryRegNddct.setParameter("desiva", desiva);
                    queryRegNddct.setParameter("txtcuenta", txtcuenta);
                    queryRegNddct.setParameter("cciva", cciva);
                    queryRegNddct.setParameter("iva", ivaCalculado);
                    queryRegNddct.setParameter("numTrans", numTrans);
                    Integer resultado = (Integer) queryRegNddct.getSingleResult();
                    System.out.println("Resultado del procedimiento11: " + resultado);
                }
            }

            // Generar número de comprobante
            String callGeneraNroComprobante = "CALL generaNroComprobante2(:codigoEmpresa, :codigoOficina, :tipoComprobante, 0)";
            Query queryGeneraNroComprobante = entityManager.createNativeQuery(callGeneraNroComprobante);
            queryGeneraNroComprobante.setParameter("codigoEmpresa", codigoEmpresa);
            queryGeneraNroComprobante.setParameter("codigoOficina", codigoOficina);
            queryGeneraNroComprobante.setParameter("tipoComprobante", 1);
            List<Object[]> resultGeneraNroComprobante = queryGeneraNroComprobante.getResultList();

            String nsecuencia = resultGeneraNroComprobante.get(0)[0].toString();
            String nsestablecimiento = resultGeneraNroComprobante.get(0)[1].toString();
            String nspuntoemision = resultGeneraNroComprobante.get(0)[2].toString();

            // Formatear el número de factura (agregar después de obtener nsecuencia, nsestablecimiento, nspuntoemision)
            if (nsestablecimiento.length() < 3) {
                nsestablecimiento = String.format("%03d", Integer.parseInt(nsestablecimiento));
            }
            if (nspuntoemision.length() < 3) {
                nspuntoemision = String.format("%03d", Integer.parseInt(nspuntoemision));
            }
            if (nsecuencia.length() < 9) {
                nsecuencia = String.format("%09d", Integer.parseInt(nsecuencia));
            }
            String numrfcta = nsestablecimiento + "-" + nspuntoemision + "-" + nsecuencia;
            // Variables para guía de remisión
            String estgremis = "";
            String pemgremis = "";
            String numgremis = "";

            Libs fecha_n = new Libs(entityManager);
            String fechaFor = fecha_n.obtenerFecha();

            String rfcta_num_guias = "";
            String rfcta_fec_emisi =  "TODAY";

            String rfcta_num_compr=null;


            // Formatear guía de remisión si existe
            if (estgremis.length() < 3) {
                estgremis = String.format("%03d", estgremis.isEmpty() ? 0 : Integer.parseInt(estgremis));
            }
            if (pemgremis.length() < 3) {
                pemgremis = String.format("%03d", pemgremis.isEmpty() ? 0 : Integer.parseInt(pemgremis));
            }
            if (numgremis.length() < 9) {
                numgremis = String.format("%09d", numgremis.isEmpty() ? 0 : Integer.parseInt(numgremis));
            }
            if (!numgremis.isEmpty() && Integer.parseInt(numgremis) > 0) {
                rfcta_num_guias = estgremis + pemgremis + numgremis;
            }

            // Descripción de la factura
            String descrip = "Venta de activos varios";
            String detalle = "Registro de Factura N.- " + numrfcta + " - Ruc: " + cedula + " - Cliente (" +
                    eliminarAcentos(beneficiario) + ") - Fec.Emision: " + LocalDate.now() + " " + descrip;
            // Borrar registros existentes antes de insertar
            String sqlDeleteDfcta = "DELETE FROM ecedfcta " +
                    "WHERE dfcta_sec_estab = :estab " +
                    "AND dfcta_sec_pemis = :pemis " +
                    "AND dfcta_num_rfcta = :rfcta " +
                    "AND dfcta_fec_emisi = :fechaEmision";
            Query queryDeleteDfcta = entityManager.createNativeQuery(sqlDeleteDfcta);
            queryDeleteDfcta.setParameter("estab", nsestablecimiento);
            queryDeleteDfcta.setParameter("pemis", nspuntoemision);
            queryDeleteDfcta.setParameter("rfcta", nsecuencia);
            queryDeleteDfcta.setParameter("fechaEmision", rfcta_fec_emisi);
            queryDeleteDfcta.executeUpdate();
            String sqlDeleteDpfct = "DELETE FROM ecedpfct " +
                    "WHERE dpfct_sec_estab = :estab " +
                    "AND dpfct_sec_pemis = :pemis " +
                    "AND dpfct_num_rfcta = :rfcta " +
                    "AND dpfct_fec_emisi = :fechaEmision";
            Query queryDeleteDpfct = entityManager.createNativeQuery(sqlDeleteDpfct);
            queryDeleteDpfct.setParameter("estab", nsestablecimiento);
            queryDeleteDpfct.setParameter("pemis", nspuntoemision);
            queryDeleteDpfct.setParameter("rfcta", nsecuencia);
            queryDeleteDpfct.setParameter("fechaEmision", rfcta_fec_emisi);
            queryDeleteDpfct.executeUpdate();


            System.err.println(nsecuencia);
            // Insertar en la tabla ecerfcta
            String sqlInsertFactura = "INSERT INTO ecerfcta (rfcta_sec_estab, rfcta_sec_pemis, rfcta_num_rfcta, rfcta_fec_emisi, rfcta_ide_rclie, " +
                    "rfcta_cod_empre, rfcta_cod_ofici, rfcta_cod_efctr, rfcta_cod_usuar, rfcta_usr_proce, rfcta_fho_proce, rfcta_cod_tdocu, rfcta_num_compr, rfcta_clv_acces, rfcta_cod_tcomp) " +
                    "VALUES (:nsestablecimiento, :nspuntoemision, :nsecuencia, TODAY, :cedula, :codigoEmpresa, :codigoOficina, 1, :codigoOperador, :codigoOperador, CURRENT, 'CDG', :rfcta_num_compr, '', 1)";
            Query queryInsertFactura = entityManager.createNativeQuery(sqlInsertFactura);
            queryInsertFactura.setParameter("nsestablecimiento", nsestablecimiento);
            queryInsertFactura.setParameter("nspuntoemision", nspuntoemision);
            queryInsertFactura.setParameter("nsecuencia", nsecuencia);
            queryInsertFactura.setParameter("cedula", cedula);
            queryInsertFactura.setParameter("codigoEmpresa", codigoEmpresa);
            queryInsertFactura.setParameter("codigoOficina", codigoOficina);
            queryInsertFactura.setParameter("codigoOperador", codigoOperador);
            queryInsertFactura.setParameter("rfcta_num_compr", rfcta_num_compr);
            queryInsertFactura.executeUpdate();

            //REGISTRO DESCRIPCION FACTURA

            // Obtener la descripción del producto
            String sqlProducto = "SELECT fprod_cod_fprod, fprod_des_fprod FROM ecefprod WHERE fprod_cod_fprod = :codfprod";
            Query queryProducto = entityManager.createNativeQuery(sqlProducto);
            queryProducto.setParameter("codfprod", codfprod);
            List<Object[]> resultProducto = queryProducto.getResultList();
            String desfprod = "";
            if (!resultProducto.isEmpty()) {
                desfprod = eliminarAcentos((String) resultProducto.get(0)[1]);
                System.err.println(desfprod);
            }
            // Obtener la descripción del servicio
            String sqlServicio = "SELECT tpser_des_tpser FROM andtpser WHERE tpser_cod_tpser = :servicio";
            Query queryServicio = entityManager.createNativeQuery(sqlServicio);
            queryServicio.setParameter("servicio", servicio);
            List<String> resultServicio = queryServicio.getResultList();
            String detfprod = "";
            if (!resultServicio.isEmpty()) {
                detfprod = eliminarAcentos(resultServicio.get(0)); // Accedemos directamente al String
                System.err.println(detfprod);
            }

            String desnuevo = detfprod;
            Integer numregisdfcta = 1;
            // Insertar en la tabla ecedfcta
            String sqlInsertFactura1 = "INSERT INTO ecedfcta (dfcta_sec_estab, dfcta_sec_pemis, dfcta_num_rfcta, dfcta_fec_emisi, dfcta_num_regis, " +
                    "dfcta_cod_fprod, dfcta_des_fprod, dfcta_num_items, dfcta_val_unida, dfcta_val_descu, dfcta_det_fprod) " +
                    "VALUES (:rfcta_sec_estab, :rfcta_sec_pemis, :rfcta_num_rfcta, TODAY, :numregisdfcta, " +
                    ":codfprod, :desnuevo, :numitems, :valunida, 0, :desnuevo)";
            Query queryInsertFactura1 = entityManager.createNativeQuery(sqlInsertFactura1);
            queryInsertFactura1.setParameter("rfcta_sec_estab", nsestablecimiento);
            queryInsertFactura1.setParameter("rfcta_sec_pemis", nspuntoemision);
            queryInsertFactura1.setParameter("rfcta_num_rfcta", nsecuencia);
            queryInsertFactura1.setParameter("numregisdfcta", numregisdfcta);
            queryInsertFactura1.setParameter("codfprod", codfprod);
            queryInsertFactura1.setParameter("desnuevo", desnuevo);
            queryInsertFactura1.setParameter("numitems", cantidad);
            queryInsertFactura1.setParameter("valunida", valunida);
            queryInsertFactura1.executeUpdate();

            String sqlFormaPago = "SELECT tfpag_des_tfpag FROM ecetfpag WHERE tfpag_cod_tfpag = :codtfpag";
            Query queryFormaPago = entityManager.createNativeQuery(sqlFormaPago);
            queryFormaPago.setParameter("codtfpag", 7); // Código de la forma de pago '7'
            // Como solo se selecciona una columna, el resultado es una lista de String, no de Object[]
            List<String> resultFormaPago = queryFormaPago.getResultList();
            String destfpag = "";
            if (!resultFormaPago.isEmpty()) {
                destfpag = resultFormaPago.get(0); // Accedemos directamente al String
            }

            double valtfpag = totalFactura;
            Integer numregisdpfct = 1;
            String sqlInsertPago = "INSERT INTO ecedpfct (dpfct_sec_estab, dpfct_sec_pemis, dpfct_num_rfcta, dpfct_fec_emisi, " +
                    "dpfct_num_regis, dpfct_cod_tfpag, dpfct_des_tfpag, dpfct_val_total, dpfct_abr_tmpfp, dpfct_num_tmpfp) " +
                    "VALUES (:rfcta_sec_estab, :rfcta_sec_pemis, :rfcta_num_rfcta, TODAY, :numregisdpfct, " +
                    ":codtfpag, :destfpag, :valtfpag, :abrtmpfp, :numtmpfp)";
            Query queryInsertPago = entityManager.createNativeQuery(sqlInsertPago);
            queryInsertPago.setParameter("rfcta_sec_estab", nsestablecimiento);
            queryInsertPago.setParameter("rfcta_sec_pemis", nspuntoemision);
            queryInsertPago.setParameter("rfcta_num_rfcta", nsecuencia);
            queryInsertPago.setParameter("numregisdpfct", numregisdpfct);
            queryInsertPago.setParameter("codtfpag", 7); // Forma de pago
            queryInsertPago.setParameter("destfpag", destfpag);
            queryInsertPago.setParameter("valtfpag", valtfpag);
            queryInsertPago.setParameter("abrtmpfp", "NINGUNO");
            queryInsertPago.setParameter("numtmpfp", "");
            queryInsertPago.executeUpdate();

            Integer rfcta_cod_efctr = 1;
            Integer modo = 1;

            if (rfcta_cod_efctr.equals(1)){
                if(modo.equals(1)){
                    String callGeneraNroComprobante1 = "CALL generaNroComprobante2(:codigoEmpresa, :codigoOficina, :tipoComprobante, :numTrans)";
                    Query queryGeneraNroComprobante1 = entityManager.createNativeQuery(callGeneraNroComprobante1);
                    queryGeneraNroComprobante1.setParameter("codigoEmpresa", codigoEmpresa);
                    queryGeneraNroComprobante1.setParameter("codigoOficina", codigoOficina);
                    queryGeneraNroComprobante1.setParameter("tipoComprobante", 1);
                    queryGeneraNroComprobante1.setParameter("numTrans", nsecuencia);
                    List<Object[]> resultGeneraNroComprobante1 = queryGeneraNroComprobante1.getResultList();
                    String nsecuencia1 = resultGeneraNroComprobante1.get(0)[0].toString();

                    // Registrar documento web
                    String callRegistraDocumentoWeb = "CALL registraDocumentoWeb2(:codigoEmpresa, :codigoOficina, :cedula, :nsestablecimiento, :nspuntoemision, :nsecuencia, :fecharegistro , :tipoComprobante, :servicio)";
                    Query queryRegistraDocumentoWeb = entityManager.createNativeQuery(callRegistraDocumentoWeb);
                    queryRegistraDocumentoWeb.setParameter("codigoEmpresa", codigoEmpresa);
                    queryRegistraDocumentoWeb.setParameter("codigoOficina", codigoOficina);
                    queryRegistraDocumentoWeb.setParameter("cedula", cedula);
                    queryRegistraDocumentoWeb.setParameter("nsestablecimiento", nsestablecimiento);
                    queryRegistraDocumentoWeb.setParameter("nspuntoemision", nspuntoemision);
                    queryRegistraDocumentoWeb.setParameter("nsecuencia", nsecuencia1);
                    queryRegistraDocumentoWeb.setParameter("tipoComprobante", 1);
                    queryRegistraDocumentoWeb.setParameter("servicio", servicio);
                    queryRegistraDocumentoWeb.setParameter("fecharegistro",fechaFor);
                    String resultado = (String) queryRegistraDocumentoWeb.getSingleResult();
                    System.out.println("Resultado del procedimiento: " + resultado);

                }
            }
            response.put("message", "Factura generada con éxito");
            response.put("status", "OK");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            response.put("message", "Error interno del servidor");
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public String obtenerSaldoDisponible(String txtcodctadp) throws Exception {
        try {
            // 1. Obtener la fecha actual del sistema llamando a un procedimiento almacenado
            String sqlFechaHora = "CALL cnxprc_fecha_hora()";
            Query queryFecha = entityManager.createNativeQuery(sqlFechaHora);
            List<Object[]> resultadoFecha = queryFecha.getResultList();

            if (resultadoFecha.isEmpty()) {
                throw new Exception("No se pudo obtener la fecha actual del sistema.");
            }
            String fecha = resultadoFecha.get(0)[0].toString().trim();
            System.out.println(fecha);
            // 2. Ejecutar el procedimiento almacenado para obtener el saldo disponible
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
        int numeroAleatorio = 100000 + random.nextInt(900000); // Asegura 6 dígitos
        return String.valueOf(numeroAleatorio);
    }

    private String eliminarAcentos(String input) {
        return input.replaceAll("[^\\p{ASCII}]", "");
    }
    private float redondearMoneda(float valor) {
        return (float) (Math.floor(valor * 100 + 0.5) / 100);
    }

}
