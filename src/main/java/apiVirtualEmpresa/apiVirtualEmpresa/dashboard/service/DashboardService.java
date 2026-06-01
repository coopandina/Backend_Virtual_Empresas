package apiVirtualEmpresa.apiVirtualEmpresa.dashboard.service;

import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import apiVirtualEmpresa.apiVirtualEmpresa.dashboard.dto.DashboardUtils;
import apiVirtualEmpresas.virtualempresas.libs.Libs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.util.*;

@Transactional
@Service

public class DashboardService {


    @PersistenceContext
    private EntityManager entityManager;
    private final JwtUtil jwtUtil;

    public DashboardService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private int intentosRealizadoTokenFallos = 0;

    //infromarcion de datos del socio
    public ResponseEntity<Map<String, Object>> informacionSocio(HttpServletRequest request, Authentication authentication) {
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

            // 2. VALIDAR AUTH
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
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (clienIdenti == null || numSocio == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER001");
                err.put("errors", "Datos del token incompletos.");
                allDataList.add(err);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String sql =
                    "SELECT TRIM(clien_ape_clien) || ' ' || TRIM(clien_nom_clien) AS nombres, " +
                            "clien_cod_clien, " +
                            "clien_cod_ofici, " +
                            "clien_tlf_domic, " +
                            "clien_dir_domic, " +
                            "ct.ctadp_sal_dispo, " +
                            "ct.ctadp_cod_ectad, " +
                            "ofi.ofici_nom_ofici, " +
                            "pr.parro_nom_parro, " +
                            "et.ectad_des_ectad, " +
                            "clien_dir_email " +
                            "FROM cnxclien " +
                            "JOIN cnxctadp ct ON ct.ctadp_cod_clien = clien_cod_clien AND ct.ctadp_cod_depos = 1 " +
                            "JOIN cnxofici ofi ON ofi.ofici_cod_ofici = clien_cod_ofici " +
                            "JOIN cnxectad et ON et.ectad_cod_ectad = ct.ctadp_cod_ectad " +
                            "JOIN cnxparro pr ON pr.parro_cod_parro = clien_dom_parro AND pr.parro_cod_ciuda = clien_dom_ciuda " +
                            "WHERE clien_cod_clien = :numSocio " +
                            "AND clien_ide_clien = :cliacRucClie";

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("numSocio", numSocio);
            query.setParameter("cliacRucClie", clienIdenti);

            List<Object[]> resultado = query.getResultList();

            if (resultado.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR003");
                err.put("errors", "No se encontraron datos del socio.");
                allDataList.add(err);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            // Solo la primera fila
            Object[] row = resultado.get(0);

            // [kguanoluisa] - Se valida si el usuario ha aceptado la ley de protección de datos - 12/05/2026
            String sqlLey = "SELECT COUNT(*) FROM andaudlpdf WHERE audlpdf_cod_canal = 8 AND audlpdf_cod_clien = :codclien";
            Query queryLey = entityManager.createNativeQuery(sqlLey);
            queryLey.setParameter("codclien", numSocio);
            Number countLey = (Number) queryLey.getSingleResult();
            boolean leyAceptada = countLey.intValue() > 0;

            Map<String, Object> data = new HashMap<>();
            data.put("nombre_socio", row[0].toString().trim());
            data.put("telefono", row[3].toString().trim());
            data.put("direccion", row[4].toString().trim());
            data.put("saldo_disponible", Libs.formatoDosDecimales(row[5].toString()));
            data.put("nombre_oficina", row[7].toString().trim());
            data.put("parroquia", row[8].toString().trim());
            data.put("estado_cuenta_desc", row[9].toString().trim());
            data.put("email", row[10].toString().trim());
            data.put("leyProteccionDatos", leyAceptada); // NUEVO CAMPO PARA FRONTEND
            data.put("status", "INFOUSEROK");

            allDataList.add(data);

            // 5. RESPUESTA FINAL
            response.put("success", true);
            response.put("AllData", allDataList);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", "Error interno del servidor.");
            err.put("status", "ERROR001");
            err.put("errors", e.getMessage());

            List<Map<String, Object>> errList = new ArrayList<>();
            errList.add(err);

            response.put("AllData", errList);

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public ResponseEntity<Map<String, Object>> inforCtaDepos(HttpServletRequest request, Authentication authentication) {
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

            // 2. VALIDAR AUTH

            if (authentication == null || !authentication.isAuthenticated()) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                allDataList.add(err);

                response.put("success", false);
                response.put("message", "No autorizado");
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

            // 4. CONSULTA 1: OBTENER OFICINA Y EMPRESA

            String sqlCodigos =
                    "SELECT clien_cod_ofici, clien_cod_empre " +
                            "FROM cnxclien " +
                            "WHERE clien_ide_clien = :cliacRucClie " +
                            "AND clien_cod_clien = :numSocio";

            Query queryCodigos = entityManager.createNativeQuery(sqlCodigos);
            queryCodigos.setParameter("cliacRucClie", clienIdenti);
            queryCodigos.setParameter("numSocio", numSocio);

            List<Object[]> datosCodigos = queryCodigos.getResultList();

            if (datosCodigos.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR003");
                err.put("errors", "No se encontraron datos del socio." + cliacUsuRuc + clienIdenti);
                allDataList.add(err);

                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            Object[] rowCodigos = datosCodigos.get(0);
            String codigoOficina = rowCodigos[0].toString();
            String codigoEmpresa = rowCodigos[1].toString();

            // 5. CONSULTA 2: CUENTAS DEL SOCIO

            String sqlCuentas =
                    "SELECT ctadp_cod_ctadp, ectad_des_ectad, depos_des_depos, " +
                            "ctadp_sal_dispo, ctadp_sal_nodis, ctadp_sal_ndchq " +
                            "FROM cnxctadp, cnxectad, cnxdepos " +
                            "WHERE ctadp_cod_empre = :codigoEmpresa " +
                            "AND ctadp_cod_ofici = :codigoOficina " +
                            "AND ctadp_cod_clien = :numSocio " +
                            "AND ctadp_cod_ectad <> '3' " +
                            "AND ctadp_cod_ectad = ectad_cod_ectad " +
                            "AND ctadp_cod_empre = depos_cod_empre " +
                            "AND ctadp_cod_ofici = depos_cod_ofici " +
                            "AND ctadp_cod_depos = depos_cod_depos";

            Query queryCuentas = entityManager.createNativeQuery(sqlCuentas);
            queryCuentas.setParameter("codigoEmpresa", codigoEmpresa);
            queryCuentas.setParameter("codigoOficina", codigoOficina);
            queryCuentas.setParameter("numSocio", numSocio);

            List<Object[]> listaCuentas = queryCuentas.getResultList();

            if (listaCuentas.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERROR002");
                err.put("errors", "No posee cuentas disponibles.");
                allDataList.add(err);

                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            for (Object[] row : listaCuentas) {
                Map<String, Object> cuenta = new HashMap<>();
                cuenta.put("ctadp_cod_ctadp", row[0].toString().trim());
                cuenta.put("ectad_des_ectad", row[1].toString().trim());
                cuenta.put("depos_des_depos", row[2].toString().trim());
                cuenta.put("ctadp_sal_dispo", Libs.formatoDosDecimales(row[3].toString()));
                cuenta.put("ctadp_sal_nodis", Libs.formatoDosDecimales(row[4].toString()));
                cuenta.put("ctadp_sal_ndchq", Libs.formatoDosDecimales(row[5].toString()));
                cuenta.put("status", "INFOUSEROK");

                allDataList.add(cuenta);
            }


            // 6. CONSULTA 3: TOTALES (CRÉDITOS / INVERSIONES)

            String sqlTotales =
                    "SELECT " +
                            " (SELECT COUNT(*) FROM cnxcredi " +
                            "   WHERE credi_cod_clien = :numSocio AND credi_cod_ecred != 5) AS total_creditos, " +
                            " (SELECT COUNT(*) FROM cnxinver " +
                            "   WHERE inver_cod_clien = :numSocio AND inver_cod_einve IN (1,2)) AS total_inversiones " +
                            "FROM systables WHERE tabid = 1";

            Query queryTot = entityManager.createNativeQuery(sqlTotales);
            queryTot.setParameter("numSocio", numSocio);

            Object[] tot = (Object[]) queryTot.getSingleResult();

            Map<String, Object> totales = new HashMap<>();
            totales.put("total_creditos", tot[0].toString());
            totales.put("total_inversiones", tot[1].toString());

            allDataList.add(totales);


            // 7. RESPUESTA FINAL

            response.put("success", true);
            response.put("AllData", allDataList);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", "Error interno del servidor.");
            err.put("status", "ERROR001");
            err.put("errors", e.getMessage());

            List<Map<String, Object>> errList = new ArrayList<>();
            errList.add(err);

            response.put("AllData", errList);

            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public ResponseEntity<Map<String, Object>> ctaPropiasTrans(HttpServletRequest request, DashboardUtils dashboardUtils, Authentication authentication) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {

            String token = Obtenertoken.desdeCookie(request);

            String cliacUsuRuc = jwtUtil.getrucIdenClie(token);
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (token == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA027");
                err.put("errors", "No autorizado: no fue posible obtener el token.");
                allDataList.add(err);

                response.put("success", false);
                response.put("AllData", allDataList);

                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            if (authentication == null || !authentication.isAuthenticated()) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                allDataList.add(err);

                response.put("success", false);
                response.put("message", "No autorizado");
                response.put("AllData", allDataList);

                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (cliacUsuRuc == null || clienIdenti == null || numSocio == null) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER001");
                err.put("errors", "Datos del token incompletos.");
                allDataList.add(err);

                response.put("AllData", allDataList);

                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            // 4. OBTENER CUENTA DEL BODY

            String ctaClient = dashboardUtils.getCodCta();


            // 5. CONSULTA SQL

            String sql = "SELECT ctadp_cod_depos, ctadp_cod_ctadp, depos_des_depos, ctadp_cod_ectad " +
                    "FROM cnxclien, cnxctadp, cnxdepos, cnxopdep " +
                    "WHERE clien_ide_clien=:clien_ide_clien " +
                    "AND ctadp_cod_empre=clien_cod_empre " +
                    "AND ctadp_cod_ofici=clien_cod_ofici " +
                    "AND ctadp_cod_clien=clien_cod_clien " +
                    "AND depos_cod_empre=ctadp_cod_empre " +
                    "AND depos_cod_ofici=ctadp_cod_ofici " +
                    "AND depos_cod_depos=ctadp_cod_depos " +
                    "AND depos_ctr_opera=0 " +
                    "AND depos_cod_moned=2 " +
                    "AND opdep_cod_empre=ctadp_cod_empre " +
                    "AND opdep_cod_ofici=ctadp_cod_ofici " +
                    "AND opdep_cod_depos=ctadp_cod_depos " +
                    "AND opdep_cod_ectad=ctadp_cod_ectad " +
                    "AND opdep_cod_toper='3' " +
                    "AND ctadp_cod_ctadp <> :ctadp_cod_ctadp " +
                    "ORDER BY ctadp_cod_depos";

            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("clien_ide_clien", cliacUsuRuc);
            query.setParameter("ctadp_cod_ctadp", ctaClient);

            List<Object[]> listCta = query.getResultList();

            if (listCta.isEmpty()) {

                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER002");
                err.put("errors", "No posee cuentas disponibles para transferir.");
                allDataList.add(err);

                response.put("AllData", allDataList);

                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // 6. ARMAR RESPUESTA

            List<Map<String, Object>> cuentas = new ArrayList<>();

            for (Object[] row : listCta) {

                Map<String, Object> cuenta = new HashMap<>();
                cuenta.put("codigoCta", row[0].toString().trim());
                cuenta.put("numeroCta", row[1].toString().trim());
                cuenta.put("descrCta", row[2].toString().trim());
                cuenta.put("estadoCta", row[3].toString().trim());

                cuenta.put("saldoCta", obtenerSaldoDisponible(row[1].toString().trim()));

                cuentas.add(cuenta);
            }

            response.put("CuentasTransferibles", cuentas);
            response.put("success", true);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            //kguanoluisa, [Se mantiene RuntimeException agregando mensaje descriptivo][N/A][22/05/2026]
            throw new RuntimeException("Error interno al consultar cuentas propias: " + e.getMessage(), e);
        }
    }


//ver informacion de terceros

    public ResponseEntity<Map<String, Object>> VerInfTerceros(HttpServletRequest request, DashboardUtils dashboardUtils) {
        Map<String, Object> response = new LinkedHashMap<>();

        try {

            String numIdentificacion = null;
            String codCta = null;
            Integer codClien = null;

            if (dashboardUtils != null && dashboardUtils.getIdTerClien() != null) {
                numIdentificacion = dashboardUtils.getIdTerClien().trim();
            }

            if (dashboardUtils != null && dashboardUtils.getCodCta() != null) {
                codCta = dashboardUtils.getCodCta().trim();
            }


            String token = Obtenertoken.desdeCookie(request);
            String numIdentificacionToken = null;

            if (token != null && !token.isBlank()) {
                try {
                    numIdentificacionToken = jwtUtil.getrucIdenClie(token);
                } catch (Exception ignored) {
                }
            }

            if ((numIdentificacion == null || numIdentificacion.isBlank()) &&
                    numIdentificacionToken != null) {
                numIdentificacion = numIdentificacionToken.trim();
            }

            if (numIdentificacion == null || numIdentificacion.isBlank()) {
                response.put("status", "ERROR001");
                response.put("message", "No se pudo determinar la identificación del cliente.");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (codCta == null || codCta.isBlank()) {
                response.put("status", "ERROR002");
                response.put("message", "No se recibió el número de cuenta." + codCta);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            String sqlCuentas = """
                        SELECT ctadp_cod_clien,
                            ofi.ofici_nom_ofici AS oficina,
                            ctadp_cod_depos,
                            ctadp_cod_ctadp,
                            dp.depos_des_depos,
                            ctadp_cod_ectad
                        FROM cnxctadp 
                        JOIN cnxofici ofi ON ofi.ofici_cod_ofici = ctadp_cod_ofici
                        JOIN cnxdepos dp ON dp.depos_cod_depos = ctadp_cod_depos 
                                         AND dp.depos_cod_ofici = ctadp_cod_ofici
                                         AND dp.depos_ctr_opera = 0
                                         AND dp.depos_cod_moned = 2
                        WHERE ctadp_cod_ctadp = :numcta
                          AND ctadp_cod_ectad IN (1,4)
                          AND ctadp_cod_depos IN (1,3)
                        ORDER BY depos_cod_depos
                    """;

            Query qCuentas = entityManager.createNativeQuery(sqlCuentas);
            qCuentas.setParameter("numcta", codCta);

            List<Object[]> rsCuentas = qCuentas.getResultList();


            List<Map<String, Object>> cuentas = new ArrayList<>();

            for (Object[] r : rsCuentas) {
                Map<String, Object> cta = new LinkedHashMap<>();
                codClien = Integer.parseInt(rsCuentas.get(0)[0].toString().trim());
                cta.put("codDepos", r[2] != null ? r[2].toString().trim() : "");
                cta.put("numCuenta", r[3] != null ? r[3].toString().trim() : "");
                cta.put("descripcion", r[4] != null ? r[4].toString().trim() : "");
                cta.put("estadoCta", r[5] != null ? r[5].toString().trim() : "");
                cuentas.add(cta);
            }

            String sqlCliente = """
                        SELECT
                            TRIM(clien_ape_clien) || ' ' || TRIM(clien_nom_clien) AS nombre_completo,
                            ofici_nom_ofici AS oficina
                        FROM cnxclien, cnxofici
                        WHERE clien_cod_clien = :txtidebenef
                          AND ofici_cod_empre = clien_cod_empre
                          AND ofici_cod_ofici = clien_cod_ofici
                    """;

            Query qCliente = entityManager.createNativeQuery(sqlCliente);
            qCliente.setParameter("txtidebenef", codClien);

            List<Object[]> rsCliente = qCliente.getResultList();

            Map<String, Object> cliente = new LinkedHashMap<>();
            if (!rsCliente.isEmpty()) {
                Object[] row = rsCliente.get(0);
                cliente.put("nombreCompleto", row[0] != null ? row[0].toString().trim() : "");
                cliente.put("oficina", row[1] != null ? row[1].toString().trim() : "");
            } else {
                cliente.put("nombreCompleto", "");
                cliente.put("oficina", "");
            }


            response.put("cliente", cliente);
            response.put("cuentas", cuentas);
            response.put("status", "OK");

            if (rsCliente.isEmpty() && rsCuentas.isEmpty()) {
                response.put("status", "ERROR002");
                response.put("message", "No se encontraron datos.");
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "ERROR003");
            error.put("message", "Error interno del servidor.");
            error.put("errors", e.getMessage());

            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, Object>> ultimosMovimientos(HttpServletRequest request, Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            String token = Obtenertoken.desdeCookie(request);

            String cliacUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (numSocio == null || numSocio.isBlank()) {
                response.put("status", "ERROR004");
                response.put("message", "Token incompleto, falta numSocio.");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String sql = """
                       SELECT andmovrec_descripcion,
                           andmovrec_ctadestino,
                           andmovrec_valor,
                           andmovrec_fecha,
                           andmovrec_titularctadestino
                       FROM andmovrec
                       WHERE andmovrec_codcliente = :numSocio
                       ORDER BY andmovrec_fecha DESC
                    """;
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("numSocio", numSocio);

            List<Object[]> resultadoMovi = query.getResultList();

            if (resultadoMovi.isEmpty()) {
                response.put("message", "No se encontraron movimientos.");
                response.put("status", "ERROR003");
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            List<Map<String, Object>> movimientos = new ArrayList<>();

            double saldoInicial = 0.00;

            for (Object[] row : resultadoMovi) {
                Map<String, Object> mov = new LinkedHashMap<>();

                mov.put("descripcion", row[0] != null ? row[0].toString().trim() : "");
                mov.put("ctaDestino", row[1] != null ? row[1].toString().trim() : "");
                mov.put("valor", row[2] != null ? row[2] : 0);
                mov.put("fecha", row[3] != null ? row[3].toString() : "");
                mov.put("titularDestino", row[4] != null ? row[4].toString().trim() : "");

                if (row[2] != null) {
                    saldoInicial += Double.parseDouble(row[2].toString());
                }
                movimientos.add(mov);
            }

            response.put("saldoInicial", formatMoneda(saldoInicial));
            response.put("movimientos", movimientos);
            response.put("status", "MOVIMIENTOK");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Error interno del servidor");
            error.put("status", "ERROR001");
            error.put("errors", e.getMessage());

            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, Object>> ultimosMovimientosFecha(HttpServletRequest request, @RequestBody DashboardUtils dashboardUtils, Authentication authentication) {

        Map<String, Object> response = new HashMap<>();

        try {
            String token = Obtenertoken.desdeCookie(request);

            String cliacUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);


            LocalDate fechaInicio = dashboardUtils.getFechaInicio();
            LocalDate fechaFin = dashboardUtils.getFechaFin();
            String codCta = dashboardUtils.getCodCta();

            if (numSocio == null || numSocio.isBlank()) {
                response.put("status", "ERROR004");
                response.put("message", "Token incompleto, falta numSocio.");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String sql = """
                       SELECT dmcta_cod_tmovi, dmcta_val_dmcta, dmcta_fec_mctad, mv.tmovi_des_tmovi
                              FROM cnxdmcta
                              JOIN cnxtmovi mv ON mv.tmovi_cod_tmovi = dmcta_cod_tmovi
                              WHERE DATE(dmcta_fec_mctad) BETWEEN :fechaInicio AND :fechaFin
                                AND dmcta_cod_ctadp = :codCta
                    """;
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("codCta", codCta);
            query.setParameter("fechaInicio", fechaInicio);
            query.setParameter("fechaFin", fechaFin);
            List<Object[]> resultadoMovi = query.getResultList();

            if (resultadoMovi.isEmpty()) {
                response.put("message", "No se encontraron movimientos.");
                response.put("status", "ERROR003");
                return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
            }

            List<Map<String, Object>> movimientos = new ArrayList<>();

            double saldoInicial = 0.00;

            for (Object[] row : resultadoMovi) {
                Map<String, Object> mov = new LinkedHashMap<>();
                mov.put("valor", row[1] != null ? row[1] : 0);
                mov.put("fecha", row[2] != null ? row[2].toString() : "");
                mov.put("descripcion", row[3] != null ? row[3].toString().trim() : "");

                if (row[1] != null) {
                    saldoInicial += Double.parseDouble(row[1].toString());
                }
                movimientos.add(mov);
            }

            response.put("saldoInicial", formatMoneda(saldoInicial));
            response.put("movimientos", movimientos);
            response.put("status", "MOVIMIENTOK");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "Error interno del servidor");
            error.put("status", "ERROR001");
            error.put("errors", e.getMessage());

            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    // PROCEDURE PARA SALDO

    private String obtenerSaldoDisponible(String numeroCta) {
        try {
            Libs fechaHoraService = new Libs(entityManager);
            String fecha = fechaHoraService.obtenerFecha();

            String sqlSaldoDisponible = "CALL cnxprc_sldos_ctadp(:codigoCuenta, :fecha)";
            Query querySaldo = entityManager.createNativeQuery(sqlSaldoDisponible);
            querySaldo.setParameter("codigoCuenta", numeroCta);
            querySaldo.setParameter("fecha", fecha);

            List<Object[]> resultadoSaldo = querySaldo.getResultList();

            if (resultadoSaldo == null || resultadoSaldo.isEmpty()) {
                return "0.00";
            }

            Object valor = resultadoSaldo.get(0)[0];
            return valor != null ? valor.toString().trim() : "0.00";

        } catch (Exception e) {
            //kguanoluisa, [Se agregó comentario a RuntimeException manteniendo el mensaje original][N/A][22/05/2026]
            throw new RuntimeException("Error al obtener el saldo disponible: " + e.getMessage(), e);
        }
    }

    private String formatMoneda(double monto) {
        return String.format("%.2f", monto);
    }

    // [kguanoluisa] - Creación de API para registrar la aceptación de ley de protección de datos mediante INSERT SELECT - 12/05/2026
    @Transactional
    public ResponseEntity<Map<String, Object>> aceptarPoliticaDatos(HttpServletRequest request, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {
            String token = Obtenertoken.desdeCookie(request);

            if (token == null || authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "No autorizado o sesión expirada.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String numSocio = jwtUtil.getcodcliente(token);

            if (numSocio == null || numSocio.isBlank()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORPOL001");
                err.put("errors", "Identificación de cliente incompleta en Token.");
                allDataList.add(err);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Verificar existencia previa para no duplicar datos
            String sqlVerifica = "SELECT COUNT(*) FROM andaudlpdf WHERE audlpdf_cod_canal = 8 AND audlpdf_cod_clien = :codclien";
            Query queryVer = entityManager.createNativeQuery(sqlVerifica);
            queryVer.setParameter("codclien", numSocio);
            Number resultExist = (Number) queryVer.getSingleResult();

            if (resultExist.intValue() > 0) {
                Map<String, Object> info = new HashMap<>();
                info.put("status", "OK");
                info.put("message", "Políticas ya registradas previamente.");
                allDataList.add(info);
                response.put("success", true);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

            // Registrar Aceptacion: Usamos SELECT anidado para extraer usuario y oficina de cnxclien
            String sqlInsert = """
                        INSERT INTO andaudlpdf (audlpdf_cod_canal, audlpdf_cod_clien, audlpdf_std_audlpdf, 
                                               audlpdf_dsmal_audlpdf, audlpdf_fec_audlpdf, audlpdf_cod_usuar, audlpdf_cod_ofici)
                        SELECT 8, clien_cod_clien, 1, 1, TODAY, clien_cod_usuar, clien_cod_ofici
                        FROM cnxclien
                        WHERE clien_cod_clien = :codclien
                    """;

            Query queryInsert = entityManager.createNativeQuery(sqlInsert);
            queryInsert.setParameter("codclien", numSocio);

            int rowsAffected = queryInsert.executeUpdate();

            if (rowsAffected > 0) {
                response.put("success", true);
                Map<String, Object> dataOk = new HashMap<>();
                dataOk.put("status", "ACEPTADO_OK");
                dataOk.put("message", "Aceptación de ley de datos registrada con éxito.");
                allDataList.add(dataOk);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.OK);
            } else {
                response.put("success", false);
                Map<String, Object> dataErr = new HashMap<>();
                dataErr.put("status", "ERRORINSERT");
                dataErr.put("errors", "No se encontró el registro del socio en cnxclien para registrar la aceptación.");
                allDataList.add(dataErr);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

        } catch (Exception e) {
            //kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback del INSERT en andaudlpdf][][2026-05-21]
            throw new RuntimeException("Error al registrar la aceptación: " + e.getMessage(), e);
        }
    }
}
