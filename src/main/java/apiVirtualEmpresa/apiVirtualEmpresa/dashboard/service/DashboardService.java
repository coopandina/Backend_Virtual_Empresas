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

import java.math.BigDecimal;
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

            // Modificado por Brayan Pallango - SQL ampliado con caja, documento, composicion, tipo y des_trans (andplina / andplexa)
            // kguanoluisa, [Se agregaron campos motivo (mctad_rzn_anula) y observacion (pmdep_det_pmdep) para replicar detalle del sistema legado][2026-09-04]
            String sql = """
                       SELECT
                           d.dmcta_cod_tmovi        AS tipo_movi,
                           d.dmcta_val_dmcta        AS valor,
                           d.dmcta_fec_mctad        AS fecha,
                           mv.tmovi_des_tmovi       AS descripcion,
                           mv.tmovi_cod_tasie       AS tipo_operacion,
                           d.dmcta_cod_cajas        AS caja,
                           c.tcomd_des_tcomd        AS documento,
                           t.ttran_abr_ttran        AS abr_ttran,
                           m.mctad_num_ttran        AS num_ttran,
                           m.mctad_rzn_anula        AS motivo,
                           p.pmdep_det_pmdep        AS observacion,
                           pl.plina_des_trans       AS des_trans_int,
                           px.plexa_des_trans       AS des_trans_ext,
                           m.mctad_cod_ttran        AS cod_ttran
                       FROM cnxdmcta d
                       JOIN cnxtmovi mv ON mv.tmovi_cod_tmovi = d.dmcta_cod_tmovi
                       JOIN cnxcajas cj ON cj.cajas_cod_cajas = d.dmcta_cod_cajas
                       JOIN cnxtcomd c  ON c.tcomd_cod_tcomd  = d.dmcta_cod_tcomd
                       LEFT JOIN cnxmctad m ON m.mctad_cod_ctadp = d.dmcta_cod_ctadp
                                          AND m.mctad_fec_mctad  = d.dmcta_fec_mctad
                                          AND m.mctad_cod_cajas  = d.dmcta_cod_cajas
                       LEFT JOIN cnxttran t ON t.ttran_cod_ttran = m.mctad_cod_ttran
                                          AND t.ttran_cod_empre = 1
                                          AND t.ttran_cod_ofici = 1
                       LEFT JOIN cnxpmdep p ON p.pmdep_fec_pmdep = m.mctad_fec_mctad
                                          AND p.pmdep_cod_empre  = m.mctad_cod_empre
                                          AND p.pmdep_cod_ofici  = m.mctad_cod_ofici
                                          AND p.pmdep_cod_ttran  = m.mctad_cod_ttran
                                          AND p.pmdep_num_ttran  = m.mctad_num_ttran
                       LEFT JOIN andplina pl ON pl.plina_num_trans = m.mctad_num_ttran
                                          AND (pl.plina_cod_ctaor = d.dmcta_cod_ctadp OR pl.plina_cod_ctade = d.dmcta_cod_ctadp)
                       LEFT JOIN andplexa px ON px.plexa_num_trans = m.mctad_num_ttran
                                          AND (px.plexa_cod_ctaor = d.dmcta_cod_ctadp OR px.plexa_cod_ctade = d.dmcta_cod_ctadp)
                       WHERE DATE(d.dmcta_fec_mctad) BETWEEN :fechaInicio AND :fechaFin
                         AND d.dmcta_cod_ctadp = :codCta
                       ORDER BY d.dmcta_fec_mctad ASC
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

            // Modificado por Brayan Pallango - Calcular saldo anterior para que el saldo acumulado cuadre
            String sqlSaldoAnt = """
                    SELECT 
                        SUM(CASE WHEN mv.tmovi_cod_tasie = 2 THEN d.dmcta_val_dmcta ELSE 0 END) - 
                        SUM(CASE WHEN mv.tmovi_cod_tasie = 1 THEN d.dmcta_val_dmcta ELSE 0 END)
                    FROM cnxdmcta d
                    JOIN cnxtmovi mv ON mv.tmovi_cod_tmovi = d.dmcta_cod_tmovi
                    WHERE d.dmcta_cod_ctadp = :codCta
                      AND DATE(d.dmcta_fec_mctad) < :fechaInicio
                    """;
            Query querySaldoAnt = entityManager.createNativeQuery(sqlSaldoAnt);
            querySaldoAnt.setParameter("codCta", codCta);
            querySaldoAnt.setParameter("fechaInicio", fechaInicio);
            Object saldoAntObj = querySaldoAnt.getSingleResult();
            
            double saldoAcumulado = 0.00;
            if (saldoAntObj != null) {
                try {
                    saldoAcumulado = Double.parseDouble(saldoAntObj.toString().replace(",", "."));
                } catch (Exception e) {}
            }

            List<Map<String, Object>> movimientos = new ArrayList<>();

            for (Object[] row : resultadoMovi) {
                Map<String, Object> mov = new LinkedHashMap<>();
                double valor = 0.0;
                if (row[1] != null) {
                    try {
                        valor = Double.parseDouble(row[1].toString().replace(",", "."));
                    } catch (Exception e) {}
                }

                // tipo_operacion: 1 = RETIRO (debito), 2 = DEPOSITO (credito)
                int tipoOperacion = 2;
                if (row[4] != null) {
                    try {
                        tipoOperacion = Double.valueOf(row[4].toString().replace(",", ".")).intValue();
                    } catch (Exception e) {}
                }

                // Calcular saldo acumulado: depósito suma, retiro resta
                if (tipoOperacion == 1) {
                    saldoAcumulado -= valor;
                } else {
                    saldoAcumulado += valor;
                }

                // Composicion / Detalle Transaccion de andplina / andplexa
                String desTransInt = (row.length > 11 && row[11] != null) ? row[11].toString().trim() : "";
                String desTransExt = (row.length > 12 && row[12] != null) ? row[12].toString().trim() : "";
                String desTransVal = !desTransInt.isEmpty() ? desTransInt : desTransExt;

                String abrTtran = row[7] != null ? row[7].toString().trim() : "";
                String numTtran = "000000";
                if (row[8] != null) {
                    try {
                        int valInt = Double.valueOf(row[8].toString().replace(",", ".")).intValue();
                        numTtran = String.format("%06d", valInt);
                    } catch (Exception e) {}
                }
                
                String composicion = !desTransVal.isEmpty() ? desTransVal : (abrTtran.isEmpty() ? "-" : abrTtran + " - " + numTtran);

                String motivoVal = row[9] != null ? row[9].toString().trim() : "";
                String obsVal = row[10] != null ? row[10].toString().trim() : "";
                String descVal = row[3] != null ? row[3].toString().trim() : "";

                String detalleBase = !obsVal.isEmpty() ? obsVal : (!motivoVal.isEmpty() ? motivoVal : descVal);
                String detalleFinal = detalleBase;

                if (!desTransVal.isEmpty()) {
                    if (!detalleFinal.contains(desTransVal)) {
                        detalleFinal = detalleFinal.isEmpty() ? desTransVal : detalleFinal + ", " + desTransVal;
                    } else if (detalleFinal.contains(" " + desTransVal) && !detalleFinal.contains(", " + desTransVal)) {
                        detalleFinal = detalleFinal.replace(" " + desTransVal, ", " + desTransVal);
                    }
                }

                mov.put("fecha",              row[2] != null ? row[2].toString() : "");
                mov.put("descripcion",        descVal);
                mov.put("tipo",               tipoOperacion == 1 ? "RETIRO" : "DEPOSITO");
                mov.put("valor",              Math.round(valor * 100.0) / 100.0);
                mov.put("saldo",              Math.round(saldoAcumulado * 100.0) / 100.0);
                mov.put("caja",               row[5] != null ? row[5].toString().trim() : "-");
                mov.put("documento",          row[6] != null ? row[6].toString().trim() : "-");
                mov.put("desTrans",           desTransVal);
                mov.put("detalleTransaccion", desTransVal);
                mov.put("composicion",        composicion);
                mov.put("motivo",             detalleFinal);
                mov.put("observacion",        detalleFinal);
                mov.put("observaciones",      detalleFinal);
                // Campos necesarios para consultar comprobante en andcomprob
                int numTtranRaw = 0;
                if (row[8] != null) {
                    try { numTtranRaw = Double.valueOf(row[8].toString().replace(",", ".")).intValue(); } catch (Exception e) {}
                }
                int codTtranRaw = 0;
                if (row.length > 13 && row[13] != null) {
                    try { codTtranRaw = Double.valueOf(row[13].toString().replace(",", ".")).intValue(); } catch (Exception e) {}
                }
                mov.put("numttran",  numTtranRaw);
                mov.put("codttran",  codTtranRaw);
                mov.put("numCuenta", codCta);

                movimientos.add(mov);
            }

            // Modificado por Brayan Pallango - Consultar datos reales del dueño de la cuenta
            String sqlCliente = """
                SELECT 
                    TRIM(c.clien_ape_clien) AS apellidos,
                    TRIM(c.clien_nom_clien) AS nombres,
                    TRIM(c.clien_dir_email) AS email,
                    TRIM(c.clien_tlf_celul) AS celular,
                    TRIM(c.clien_ide_clien) AS cedula
                FROM cnxctadp a
                JOIN cnxclien c ON c.clien_cod_clien = a.ctadp_cod_clien
                WHERE a.ctadp_cod_ctadp = :codCta
            """;
            Query queryCliente = entityManager.createNativeQuery(sqlCliente);
            queryCliente.setParameter("codCta", codCta);
            List<Object[]> resCliente = queryCliente.getResultList();
            
            Map<String, String> clienteInfo = new HashMap<>();
            if (!resCliente.isEmpty()) {
                Object[] rowC = resCliente.get(0);
                String ape = rowC[0] != null ? rowC[0].toString() : "";
                String nom = rowC[1] != null ? rowC[1].toString() : "";
                clienteInfo.put("nombre", (ape + " " + nom).trim());
                clienteInfo.put("email", rowC[2] != null ? rowC[2].toString() : "");
                clienteInfo.put("telefono", rowC[3] != null ? rowC[3].toString() : "");
                clienteInfo.put("cedula", rowC[4] != null ? rowC[4].toString() : "");
            }
            
            response.put("clienteInfo", clienteInfo);
            
            response.put("saldoFinal", formatMoneda(saldoAcumulado));
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

    public ResponseEntity<Map<String, Object>> obtenerComprobante(HttpServletRequest request, Map<String, Object> reqBody, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            System.out.println("=== DEBUG OBTENER COMPROBANTE ===");
            System.out.println("Payload recibido en reqBody: " + reqBody);

            String token = Obtenertoken.desdeCookie(request);
            if (token == null || authentication == null || !authentication.isAuthenticated()) {
                System.out.println("DEBUG COMPROBANTE: No autorizado (token o authentication es nulo)");
                response.put("success", false);
                response.put("message", "No autorizado");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            Object numttranObj = reqBody != null ? reqBody.get("numttran") : null;
            Object codttranObj = reqBody != null ? reqBody.get("codttran") : null;
            Object ctaOrigenObj = reqBody != null ? reqBody.get("numCuenta") : null;
            Object valorObj = reqBody != null ? reqBody.get("valor") : null;

            Integer numttran = null;
            if (numttranObj != null) {
                try {
                    numttran = Integer.parseInt(numttranObj.toString().trim());
                } catch (Exception e) {}
            }

            Integer codttran = null;
            if (codttranObj != null) {
                try {
                    codttran = Integer.parseInt(codttranObj.toString().trim());
                } catch (Exception e) {}
            }

            String ctaOrigen = ctaOrigenObj != null ? ctaOrigenObj.toString().trim() : null;
            BigDecimal valor = null;
            if (valorObj != null) {
                try {
                    valor = new BigDecimal(valorObj.toString().trim());
                } catch (Exception e) {}
            }

            System.out.println("DEBUG COMPROBANTE -> Parsed params: numttran=[" + numttran + "], codttran=[" + codttran + "], ctaOrigen=[" + ctaOrigen + "], valor=[" + valor + "]");

            String baseSql = "SELECT comprob_cod_comprob, comprob_cod_ttran, comprob_num_ttran, " +
                    "TRIM(comprob_nom_clien), TRIM(comprob_cod_clienori), TRIM(comprob_cod_ctadp), " +
                    "TRIM(comprob_des_emalori), TRIM(comprob_tlf_cliori), TRIM(comprob_nom_entori), " +
                    "TRIM(comprob_nom_dest), TRIM(comprob_cod_cliendes), TRIM(comprob_num_ctadest), " +
                    "TRIM(comprob_ide_dest), TRIM(comprob_des_emaldes), TRIM(comprob_tlf_clides), " +
                    "TRIM(comprob_nom_entdes), comprob_fec_trans, comprob_val_trans, " +
                    "comprob_val_cmsion, TRIM(comprob_des_trans) " +
                    "FROM andcomprob ";

            List<Object[]> results = new ArrayList<>();

            // Intento 1: numttran + codttran
            if (numttran != null && numttran > 0 && codttran != null && codttran > 0) {
                String sql1 = baseSql + "WHERE comprob_num_ttran = :numttran AND comprob_cod_ttran = :codttran ORDER BY comprob_cod_comprob DESC";
                System.out.println("DEBUG COMPROBANTE Intento 1 -> numttran=" + numttran + ", codttran=" + codttran);
                try {
                    Query q1 = entityManager.createNativeQuery(sql1);
                    q1.setParameter("numttran", numttran);
                    q1.setParameter("codttran", codttran);
                    results = q1.getResultList();
                    System.out.println("DEBUG COMPROBANTE Intento 1 -> Registros encontrados: " + results.size());
                } catch (Exception e1) {
                    System.out.println("DEBUG COMPROBANTE Error Intento 1: " + e1.getMessage());
                }
            }

            // Intento 2: numttran solo
            if (results.isEmpty() && numttran != null && numttran > 0) {
                String sql2 = baseSql + "WHERE comprob_num_ttran = :numttran ORDER BY comprob_cod_comprob DESC";
                System.out.println("DEBUG COMPROBANTE Intento 2 -> numttran=" + numttran);
                try {
                    Query q2 = entityManager.createNativeQuery(sql2);
                    q2.setParameter("numttran", numttran);
                    results = q2.getResultList();
                    System.out.println("DEBUG COMPROBANTE Intento 2 -> Registros encontrados: " + results.size());
                } catch (Exception e2) {
                    System.out.println("DEBUG COMPROBANTE Error Intento 2: " + e2.getMessage());
                }
            }

            // Intento 3: cuentaOrigen + valor
            if (results.isEmpty() && ctaOrigen != null && !ctaOrigen.isEmpty() && valor != null) {
                String sql3 = baseSql + "WHERE comprob_cod_ctadp = :ctaOrigen AND comprob_val_trans = :valor ORDER BY comprob_cod_comprob DESC";
                System.out.println("DEBUG COMPROBANTE Intento 3 -> ctaOrigen=" + ctaOrigen + ", valor=" + valor);
                try {
                    Query q3 = entityManager.createNativeQuery(sql3);
                    q3.setParameter("ctaOrigen", ctaOrigen);
                    q3.setParameter("valor", valor);
                    results = q3.getResultList();
                    System.out.println("DEBUG COMPROBANTE Intento 3 -> Registros encontrados: " + results.size());
                } catch (Exception e3) {
                    System.out.println("DEBUG COMPROBANTE Error Intento 3: " + e3.getMessage());
                }
            }

            if (!results.isEmpty()) {
                Object[] row = results.get(0);
                Map<String, Object> data = new HashMap<>();
                String nomClienVal = row[3] != null ? row[3].toString().trim() : "";
                String codClienOriVal = row[4] != null ? row[4].toString().trim() : "";
                String ctaOriVal = row[5] != null ? row[5].toString().trim() : "";
                String emalOriVal = row[6] != null ? row[6].toString().trim() : "";
                String tlfOriVal = row[7] != null ? row[7].toString().trim() : "";
                String entOriVal = row[8] != null ? row[8].toString().trim() : "";

                String nomDestVal = row[9] != null ? row[9].toString().trim() : "";
                String codClienDesVal = row[10] != null ? row[10].toString().trim() : "";
                String ctaDestVal = row[11] != null ? row[11].toString().trim() : "";
                String ideDestVal = row[12] != null ? row[12].toString().trim() : "";
                String emalDesVal = row[13] != null ? row[13].toString().trim() : "";
                String tlfDesVal = row[14] != null ? row[14].toString().trim() : "";
                String entDesVal = row[15] != null ? row[15].toString().trim() : "";

                // Rescate dinámico si faltan datos de origen
                if (!ctaOriVal.isEmpty() && (nomClienVal.isEmpty() || codClienOriVal.isEmpty() || emalOriVal.isEmpty() || tlfOriVal.isEmpty() || entOriVal.isEmpty())) {
                    try {
                        String sqlOriFull = "SELECT " +
                                "c.clien_ape_clien, " +
                                "c.clien_nom_clien, " +
                                "c.clien_cod_clien, " +
                                "c.clien_dir_email, " +
                                "c.clien_tlf_celul, " +
                                "o.ofici_nom_ofici " +
                                "FROM cnxctadp a " +
                                "JOIN cnxclien c ON c.clien_cod_clien = a.ctadp_cod_clien " +
                                "JOIN cnxofici o ON o.ofici_cod_ofici = c.clien_cod_ofici " +
                                "WHERE a.ctadp_cod_ctadp = :cta";
                        Query qOri = entityManager.createNativeQuery(sqlOriFull);
                        qOri.setParameter("cta", ctaOriVal);
                        List<Object[]> rsOri = qOri.getResultList();
                        if (!rsOri.isEmpty()) {
                            Object[] rOri = rsOri.get(0);
                            String apeOri = rOri[0] != null ? rOri[0].toString().trim() : "";
                            String nomOri = rOri[1] != null ? rOri[1].toString().trim() : "";
                            if (nomClienVal.isEmpty()) nomClienVal = (apeOri + " " + nomOri).trim();
                            if (codClienOriVal.isEmpty() && rOri[2] != null) codClienOriVal = rOri[2].toString().trim();
                            if (emalOriVal.isEmpty() && rOri[3] != null) emalOriVal = rOri[3].toString().trim();
                            if (tlfOriVal.isEmpty() && rOri[4] != null) tlfOriVal = rOri[4].toString().trim();
                            if (entOriVal.isEmpty() && rOri[5] != null) entOriVal = rOri[5].toString().trim();
                        }
                    } catch (Exception exOri) {
                        System.out.println("Aviso al rescatar datos origen: " + exOri.getMessage());
                    }
                }

                // Rescate dinámico si faltan datos de destino
                if (!ctaDestVal.isEmpty() && (nomDestVal.isEmpty() || codClienDesVal.isEmpty() || emalDesVal.isEmpty() || tlfDesVal.isEmpty() || entDesVal.isEmpty())) {
                    try {
                        String sqlDesFull = "SELECT " +
                                "c.clien_ape_clien, " +
                                "c.clien_nom_clien, " +
                                "c.clien_cod_clien, " +
                                "c.clien_dir_email, " +
                                "c.clien_tlf_celul, " +
                                "o.ofici_nom_ofici, " +
                                "c.clien_ide_clien " +
                                "FROM cnxctadp a " +
                                "JOIN cnxclien c ON c.clien_cod_clien = a.ctadp_cod_clien " +
                                "JOIN cnxofici o ON o.ofici_cod_ofici = c.clien_cod_ofici " +
                                "WHERE a.ctadp_cod_ctadp = :cta";
                        Query qDes = entityManager.createNativeQuery(sqlDesFull);
                        qDes.setParameter("cta", ctaDestVal);
                        List<Object[]> rsDes = qDes.getResultList();
                        if (!rsDes.isEmpty()) {
                            Object[] rDes = rsDes.get(0);
                            String apeDes = rDes[0] != null ? rDes[0].toString().trim() : "";
                            String nomDes = rDes[1] != null ? rDes[1].toString().trim() : "";
                            if (nomDestVal.isEmpty()) nomDestVal = (apeDes + " " + nomDes).trim();
                            if (codClienDesVal.isEmpty() && rDes[2] != null) codClienDesVal = rDes[2].toString().trim();
                            if (emalDesVal.isEmpty() && rDes[3] != null) emalDesVal = rDes[3].toString().trim();
                            if (tlfDesVal.isEmpty() && rDes[4] != null) tlfDesVal = rDes[4].toString().trim();
                            if (entDesVal.isEmpty() && rDes[5] != null) entDesVal = rDes[5].toString().trim();
                            if (ideDestVal.isEmpty() && rDes[6] != null) ideDestVal = rDes[6].toString().trim();
                        }
                    } catch (Exception exDes) {
                        System.out.println("Aviso al rescatar datos destino: " + exDes.getMessage());
                    }
                }

                data.put("codComprobante", row[0]);
                data.put("codTtran", row[1]);
                data.put("numTtran", row[2] != null ? String.format("%06d", Integer.parseInt(row[2].toString().trim())) : "000000");

                data.put("nombreCliente", nomClienVal);
                data.put("codigoClienteOrigen", codClienOriVal);
                data.put("cuentaOrigen", ctaOriVal);
                data.put("emailOrigen", emalOriVal);
                data.put("telefonoOrigen", tlfOriVal);
                data.put("entidadOrigen", entOriVal);

                data.put("nombreBeneficiario", nomDestVal);
                data.put("codigoClienteDestino", codClienDesVal);
                data.put("cuentaDestino", ctaDestVal);
                data.put("identificacionDestino", ideDestVal);
                data.put("emailDestino", emalDesVal);
                data.put("telefonoDestino", tlfDesVal);
                data.put("entidadDestino", entDesVal);

                data.put("fechaExacta", row[16] != null ? row[16].toString() : "");
                data.put("monto", row[17]);
                data.put("comision", row[18]);
                data.put("detalleTransaccion", row[19]);

                System.out.println("DEBUG COMPROBANTE EXITOSO -> Datos a devolver: " + data);
                response.put("success", true);
                response.put("data", data);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

            System.out.println("DEBUG COMPROBANTE: No se encontro ninguna coincidencia en andcomprob.");
            response.put("success", false);
            response.put("message", "No se encontró registro del comprobante para esta transacción.");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            System.out.println("=== ERROR CRITICO AL CONSULTAR COMPROBANTE ===");
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error al consultar comprobante: " + e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }
}
