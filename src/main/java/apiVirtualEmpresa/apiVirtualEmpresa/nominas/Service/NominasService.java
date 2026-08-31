package apiVirtualEmpresa.apiVirtualEmpresa.nominas.Service;

import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.config.Obtenertoken;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.TokenExpirationService;
import apiVirtualEmpresa.apiVirtualEmpresa.nominas.dto.NominasUtils;
import apiVirtualEmpresas.virtualempresas.libs.Libs;
import envioCorreo.sendEmail;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import sms.SendSMS;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Transactional
@Service
public class NominasService {

    @PersistenceContext
    private EntityManager entityManager;

    private final JwtUtil jwtUtil;

    public NominasService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Autowired
    private TokenExpirationService tokenExpirationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private apiVirtualEmpresa.apiVirtualEmpresa.services.MetodoPagoClientService metodoPagoClientService;

    int intentosRealizadoTokenFallos = 0;
    int intentosRealizadoTokenFallosInterban = 0;


    public ResponseEntity<Map<String, Object>> listarDatosNominaInterna(HttpServletRequest request, Authentication authentication, List<NominasUtils> requestDataList) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {
            // 1. TOKEN DESDE COOKIE
            String token = Obtenertoken.desdeCookie(request);

            // VALIDACIONES GLOBALES
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
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            // 2. RECORRER CADA OBJETO DEL ARRAY JSON RECIBIDO
            for (NominasUtils item : requestDataList) {

                String ideClien = item.getIdeClien();
                String ctaDestino = item.getCtaDestino();

                // VALIDACIÓN DE DATOS DE CADA OBJETO
                if (ideClien == null || ctaDestino == null) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("message", "Datos del socio incompletos");
                    err.put("status", "AA022");
                    err.put("error", "ERROR EN ENVIO DE DATOS");
                    allDataList.add(err);
                    continue; // pasa al siguiente item del arreglo
                }

                // --- AQUÍ DEBES PONER TU SQL Y LÓGICA DE CONSULTA ---
                // Ejemplo:
                String sql =
                        "SELECT TRIM(cl.clien_ape_clien) || ' ' || TRIM(cl.clien_nom_clien) AS nombres, " +
                                "ctadp_cod_clien, ctadp_cod_ofici, ctadp_sal_dispo " +
                                "FROM cnxctadp " +
                                "JOIN cnxclien cl ON cl.clien_cod_clien = ctadp_cod_clien " +
                                "                 AND cl.clien_ide_clien = :clienIdenti " +
                                "WHERE ctadp_cod_ctadp = :ctaDestino";

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("clienIdenti", ideClien);
                query.setParameter("ctaDestino", ctaDestino);

                List<Object[]> results = query.getResultList();

                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "ERRORTRFINTER002");
                    err.put("errors", "Cuenta no encontrada o no pertenece al socio.");
                    allDataList.add(err);
                    continue;
                }

                // ARMAR RESPUESTA
                Libs fechaHoraService = new Libs(entityManager);
                String fecha = fechaHoraService.obtenerFechaYHora();
                int i = 1;

                for (Object[] row : results) {
                    Map<String, Object> datos = new HashMap<>();
                    datos.put("fecha", fecha);
                    datos.put("registros", i++);
                    datos.put("nombres", row[0].toString());

                    double saldo = row[3] != null ? Double.parseDouble(row[3].toString()) : 0.0;
                    datos.put("saldoCta", saldo);

                    allDataList.add(datos);
                }
            }

            // RESPUESTA FINAL
            response.put("success", true);
            response.put("DatosNominaInternos", allDataList);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERRORTRFINTER500");
            err.put("errors", e.getMessage());

            response.put("AllData", Collections.singletonList(err));
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, Object>> numNomina(HttpServletRequest request, Authentication authentication, NominasUtils requestData) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {
            String token = Obtenertoken.desdeCookie(request);
            String cliacUsuRuc = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (token == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA027");
                err.put("errors", "No autorizado: no fue posible obtener el token.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (cliacUsuRuc == null || numSocio == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER001");
                err.put("errors", "Datos del token incompletos.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            String valservi = requestData.getValservi();
            String tipestado = requestData.getTipestado();
            String ctaOrigen = requestData.getCtaOrigen();

            if (valservi == null || tipestado == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA022");
                err.put("errors", "Datos incompletos: para número de nóminas. " + valservi + " : " + tipestado + " : " + cliacUsuRuc);
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            String sql;

            //kguanoluisa, [Se modifico query numNomina valservi=2 para incluir cnxifina y se quitaron filtros codifina. En valservi=1 se igualo a 3 columnas. Se mapean cod_banco y nom_banco en JSON][numnomina, ifina_nom_ifina, plexa_cod_ifina][22/05/2026]
            if (valservi.equals("2")) {
                sql = """
                            SELECT DISTINCT plexa_num_plnex AS numnomina, 
                                   COALESCE(ifi.ifina_nom_ifina, etc.etcptec_des_entid) AS ifina_nom_ifina, 
                                   COALESCE(plexa_cod_ifina, plexa_cod_etcptec) AS plexa_cod_ifina,
                                   plexa_tip_trans
                            FROM andplexa
                            LEFT JOIN cnxifina ifi ON ifi.ifina_cod_ifina = plexa_cod_ifina
                            LEFT JOIN andetcptec etc ON etc.etcptec_cod_etcptec = plexa_cod_etcptec
                            WHERE plexa_ide_clien = :txtideclien
                              AND plexa_cod_ctrnomna = :tipestad
                              AND plexa_cod_ctaor = :codctadp
                        """;
            } else {
                sql = """
                            SELECT DISTINCT plina_num_plina AS numnomina, 'INTERNA' AS ifina_nom_ifina, 0 AS plexa_cod_ifina
                            FROM andplina
                            WHERE plina_cod_ctaor = :codctadp
                              AND plina_ctr_trans = :tipestad
                        """;
            }
            Query query = entityManager.createNativeQuery(sql);
            query.setParameter("tipestad", tipestado.equals("3") ? "0" : tipestado);

            if (valservi.equals("2")) {
                query.setParameter("txtideclien", cliacUsuRuc);
                query.setParameter("codctadp", ctaOrigen);
            } else {
                query.setParameter("codctadp", ctaOrigen);
            }

            List<Object[]> results = query.getResultList();

            if (results.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "NOMINA404");
                err.put("errors", "No existen registros de nómina para los filtros enviados.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

            int i = 1;

            for (Object[] row : results) {

                Map<String, Object> datos = new HashMap<>();

                datos.put("registros", i++);
                datos.put("codnomina", row[0] != null ? row[0].toString().trim() : "");
                datos.put("desnomina", row[0] != null ? row[0].toString().trim() : "");

                if (valservi.equals("2")) {
                    datos.put("nom_banco", row[1] != null ? row[1].toString().trim() : "");
                    datos.put("cod_banco", row[2] != null ? row[2].toString().trim() : "");
                    datos.put("tip_trans", row[3] != null ? row[3].toString().trim() : "");
                }

                allDataList.add(datos);
            }


            //    7) RESPUESTA FINAL

            response.put("success", true);
            response.put("DatosNomina", allDataList);
            return new ResponseEntity<>(response, HttpStatus.OK);


        } catch (Exception e) {

            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERRORNOMINA500");
            err.put("errors", e.getMessage());

            response.put("AllData", List.of(err));
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public ResponseEntity<Map<String, Object>> listarNominaAcreditar(HttpServletRequest request, Authentication authentication, NominasUtils requestData) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();
        try {

            //  VALIDAR TOKEN

            String token = Obtenertoken.desdeCookie(request);
            String cliacUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (cliacUsuVirtu == null || numSocio == null || clienIdenti == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER001");
                err.put("errors", "Datos del token incompletos.");
                allDataList.add(err);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            if (token == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA027");
                err.put("errors", "No autorizado: no fue posible obtener el token.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String estado = "1";
            String ctadp = requestData.getCtadp();
            String numnomina = requestData.getNumnomina();
            String valservi = requestData.getValservi();

            if (estado == null || numnomina == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA022");
                err.put("errors", "Datos incompletos: estado, numnomina y ctaor son obligatorios.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            if (valservi.equals("2")) {
                String codbanco = requestData.getCodbanco();
                if (codbanco == null) {
                    codbanco = "";
                }

                String sql;
                sql = """
                        SELECT plexa_ide_desti, plexa_nom_desti, plexa_cod_ctade, plexa_val_trans, plexa_cod_plexa, plexa_des_plexa,
                               etc.etcptec_cod_recept AS fiCode, etc.etcptec_cod_ababin AS aba, plexa_cod_tcude AS tipoCuenta,
                               COALESCE(plexa_cod_ifina, plexa_cod_etcptec) AS codbanco, plexa_tlf_desti
                        FROM andplexa
                        LEFT JOIN andetcptec etc ON etc.etcptec_cod_etcptec = plexa_cod_etcptec
                        WHERE plexa_ide_clien = :ideclien 
                        AND (:cntbnco = '' OR :cntbnco IS NULL OR plexa_cod_ifina = :cntbnco OR plexa_cod_etcptec = :cntbnco) 
                        AND plexa_num_plnex = :numnomina
                        AND plexa_cod_ctrnomna = :estado 
                        """;


                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("ideclien", clienIdenti);
                query.setParameter("cntbnco", codbanco);
                query.setParameter("numnomina", numnomina);
                query.setParameter("estado", estado);

                List<Object[]> results = query.getResultList();


                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "NOMINA404");
                    err.put("errors", "No existen registros de nómina para los filtros enviados." + clienIdenti + ":" + codbanco + ":" + numnomina + ":" + estado);
                    response.put("success", false);
                    response.put("AllData", List.of(err));
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }

                int i = 1;

                for (Object[] row : results) {
                    Map<String, Object> datos = new HashMap<>();

                    datos.put("registros", i++);

                    datos.put("ideClien",
                            row[0] != null ? row[0].toString().trim() : null);

                    datos.put("nomapecl",
                            row[1] != null ? row[1].toString().trim() : null);

                    datos.put("ctadestin",
                            row[2] != null ? row[2].toString().trim() : null);

                    datos.put("mnttransf",
                            row[3] != null ? row[3].toString().trim() : null);

                    datos.put("codreg",
                            row[4] != null ? row[4].toString().trim() : null);

                    datos.put("descripcion",
                            row[5] != null ? row[5].toString().trim() : null);

                    datos.put("fiCode",
                            row[6] != null ? row[6].toString().trim() : "");

                    datos.put("aba",
                            row[7] != null ? row[7].toString().trim() : "");

                    datos.put("tipoCuenta",
                            row[8] != null ? row[8].toString().trim() : "");

                    datos.put("codbanco",
                            row[9] != null ? row[9].toString().trim() : "");

                    datos.put("plexaTlfDesti",
                            row[10] != null ? row[10].toString().trim() : "");

                    allDataList.add(datos);
                }

            } else {


                String sql;
                sql = """
                          SELECT plina_cod_ctade, plina_val_trans, plina_ctr_trans,plina_fec_carga,ct.ctadp_cod_clien,trim(cl.clien_ape_clien) || ' ' || trim(cl.clien_nom_clien) AS nombres, plina_fec_aprob, cl.clien_ide_clien, plina_cod_plina, plina_des_plina
                          FROM andplina JOIN cnxctadp ct ON ct.ctadp_cod_ctadp = plina_cod_ctade
                          JOIN cnxclien cl ON cl.clien_cod_clien = ct.ctadp_cod_clien
                        WHERE plina_cod_ctaor = :ctadp
                        AND plina_num_plina = :numnomina
                        AND plina_ctr_trans = :estado
                        """;


                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("ctadp", ctadp);
                query.setParameter("numnomina", numnomina);
                query.setParameter("estado", estado);

                List<Object[]> results = query.getResultList();

                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "NOMINA404");
                    err.put("errors", "No existen registros de nómina para los filtros enviados.");
                    response.put("success", false);
                    response.put("AllData", List.of(err));
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }

                int i = 1;


                for (Object[] row : results) {
                    Map<String, Object> datos = new HashMap<>();

                    datos.put("registros", i++);

                    datos.put("ctadp_acredita",
                            row[0] != null ? row[0].toString().trim() : null);

                    datos.put("val_acredita",
                            row[1] != null ? row[1].toString().trim() : null);

                    datos.put("fec_carga",
                            row[3] != null ? row[3].toString().trim() : null);

                    datos.put("nombres",
                            row[5] != null ? row[5].toString().trim() : null);

                    datos.put("fec_aprobado",
                            row[6] != null ? row[6].toString().trim() : "N/A");
                    datos.put("ide_cliente",
                            row[7] != null ? row[7].toString().trim() : "N/A");

                    datos.put("codreg",
                            row[8] != null ? row[8].toString().trim() : "N/A");

                    datos.put("descripcion",
                            row[9] != null ? row[9].toString().trim() : "N/A");
                    allDataList.add(datos);
                }
            }
            response.put("success", true);
            response.put("DatosNomina", allDataList);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {

            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERRORNOMINA500");
            err.put("errors", e.getMessage());

            response.put("AllData", List.of(err));
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, Object>> listarNomina(HttpServletRequest request, Authentication authentication, NominasUtils requestData) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();
        try {

            //  VALIDAR TOKEN

            String token = Obtenertoken.desdeCookie(request);
            String cliacUsuVirtu = authentication.getName();
            String numSocio = jwtUtil.getcodcliente(token);
            String clienIdenti = jwtUtil.getrucIdenClie(token);

            if (cliacUsuVirtu == null || numSocio == null || clienIdenti == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER001");
                err.put("errors", "Datos del token incompletos.");
                allDataList.add(err);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            if (token == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA027");
                err.put("errors", "No autorizado: no fue posible obtener el token.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String estado = requestData.getEstado();
            String ctadp = requestData.getCtadp();
            String numnomina = requestData.getNumnomina();
            String valservi = requestData.getValservi();
            LocalDate fechaInicio = requestData.getFechaInicio();
            LocalDate fechaFin = requestData.getFechaFin();

            if (estado == null || numnomina == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA022");
                err.put("errors", "Datos incompletos: estado, numnomina y ctaor son obligatorios.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (valservi.equals("2")) {
                String codbanco = requestData.getCodbanco();
                if (codbanco == null) {
                    codbanco = "";
                }

                String sql;
                if (estado.equals("0") || estado.equals("3")) {
                    sql = """
                            SELECT plexa_ide_desti, plexa_nom_desti, plexa_cod_ctade, plexa_val_trans, plexa_fec_carga, plexa_des_desti FROM andplexa
                            WHERE plexa_ide_clien = :ideclien AND (:cntbnco = '' OR :cntbnco IS NULL OR plexa_cod_ifina = :cntbnco OR plexa_cod_etcptec = :cntbnco) AND plexa_num_plnex = :numnomina
                            AND plexa_cod_ctrnomna = :estado AND DATE(plexa_fec_aprob) BETWEEN :inicio AND :fin
                            """;
                } else {
                    sql = """
                            SELECT plexa_ide_desti, plexa_nom_desti, plexa_cod_ctade, plexa_val_trans, plexa_fec_carga, plexa_des_desti
                            FROM andplexa WHERE plexa_ide_clien = :ideclien AND (:cntbnco = '' OR :cntbnco IS NULL OR plexa_cod_ifina = :cntbnco OR plexa_cod_etcptec = :cntbnco) AND plexa_num_plnex = :numnomina
                            AND plexa_cod_ctrnomna = :estado AND DATE(plexa_fec_carga) BETWEEN :inicio AND :fin
                            """;
                }

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("ideclien", clienIdenti);
                query.setParameter("cntbnco", codbanco);
                query.setParameter("numnomina", numnomina);
                query.setParameter("estado", estado);
                query.setParameter("inicio", fechaInicio);
                query.setParameter("fin", fechaFin);

                List<Object[]> results = query.getResultList();


                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "NOMINA404");
                    err.put("errors", "No existen registros de nómina para los filtros enviados.");
                    response.put("success", false);
                    response.put("AllData", List.of(err));
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }

                int i = 1;

                for (Object[] row : results) {
                    Map<String, Object> datos = new HashMap<>();

                    datos.put("registros", i++);

                    datos.put("ideClien",
                            row[0] != null ? row[0].toString().trim() : null);

                    datos.put("nomapecl",
                            row[1] != null ? row[1].toString().trim() : null);

                    datos.put("ctadestin",
                            row[2] != null ? row[2].toString().trim() : null);

                    datos.put("mnttransf",
                            row[3] != null ? row[3].toString().trim() : null);

                    datos.put("fec_carga",
                            row[4] != null ? row[4].toString().trim() : null);
                    datos.put("motivoFallo",
                            row[5] != null ? row[5].toString().trim() : "Transacción no procesada");

                    allDataList.add(datos);
                }

            } else {


                String sql;

                if (estado.equals("0") || estado.equals("3")) {
                    sql = """
                                SELECT plina_cod_ctade, plina_val_trans, plina_ctr_trans,plina_fec_carga,ct.ctadp_cod_clien,trim(cl.clien_ape_clien) || ' ' || trim(cl.clien_nom_clien) AS nombres, plina_fec_aprob, cl.clien_ide_clien
                                            FROM andplina JOIN cnxctadp ct ON ct.ctadp_cod_ctadp = plina_cod_ctade
                                            JOIN cnxclien cl ON cl.clien_cod_clien = ct.ctadp_cod_clien
                                WHERE plina_cod_ctaor = :ctadp
                                  AND plina_num_plina = :numnomina
                                  AND plina_ctr_trans = :estado
                                  AND DATE(plina_fec_aprob) BETWEEN :inicio AND :fin
                            """;
                } else {
                    sql = """
                                SELECT plina_cod_ctade, plina_val_trans, plina_ctr_trans, plina_fec_carga,ct.ctadp_cod_clien,trim(cl.clien_ape_clien) || ' ' || trim(cl.clien_nom_clien) AS nombres, plina_fec_aprob, cl.clien_ide_clien
                                            FROM andplina JOIN cnxctadp ct ON ct.ctadp_cod_ctadp = plina_cod_ctade
                                            JOIN cnxclien cl ON cl.clien_cod_clien = ct.ctadp_cod_clien
                                WHERE plina_cod_ctaor = :ctadp
                                  AND plina_num_plina = :numnomina
                                  AND plina_ctr_trans = :estado
                                  AND DATE(plina_fec_carga) BETWEEN :inicio AND :fin
                            """;
                }

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("ctadp", ctadp);
                query.setParameter("numnomina", numnomina);
                query.setParameter("estado", estado);
                query.setParameter("inicio", fechaInicio);
                query.setParameter("fin", fechaFin);

                List<Object[]> results = query.getResultList();


                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "NOMINA404");
                    err.put("errors", "No existen registros de nómina para los filtros enviados.");
                    response.put("success", false);
                    response.put("AllData", List.of(err));
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }

                int i = 1;


                for (Object[] row : results) {
                    Map<String, Object> datos = new HashMap<>();

                    datos.put("registros", i++);

                    datos.put("ctadp_acredita",
                            row[0] != null ? row[0].toString().trim() : null);

                    datos.put("val_acredita",
                            row[1] != null ? row[1].toString().trim() : null);

                    datos.put("fec_carga",
                            row[3] != null ? row[3].toString().trim() : null);

                    datos.put("nombres",
                            row[5] != null ? row[5].toString().trim() : null);

                    datos.put("ide_cliente",
                            row[7] != null ? row[7].toString().trim() : "N/A");

                    datos.put("fec_aprobado",
                            row[6] != null ? row[6].toString().trim() : "N/A");

                    allDataList.add(datos);
                }
            }
            response.put("success", true);
            response.put("DatosNomina", allDataList);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {

            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERRORNOMINA500");
            err.put("errors", e.getMessage());

            response.put("AllData", List.of(err));
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, Object>> listarEstadoNomina(HttpServletRequest request, Authentication authentication, NominasUtils requestData) {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();
        try {

            //  VALIDAR TOKEN

            String token = Obtenertoken.desdeCookie(request);
            String cliacUsuVirtu = authentication.getName();
            String numSocio = jwtUtil.getcodcliente(token);
            String clienIdenti = jwtUtil.getrucIdenClie(token);

            if (cliacUsuVirtu == null || numSocio == null || clienIdenti == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "ERRORTRFINTER001");
                err.put("errors", "Datos del token incompletos.");
                allDataList.add(err);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            if (token == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA027");
                err.put("errors", "No autorizado: no fue posible obtener el token.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            String estado = requestData.getEstado();
            String ctadp = requestData.getCtadp();
            String numnomina = requestData.getNumnomina();
            String valservi = requestData.getValservi();
            LocalDate fechaInicio = requestData.getFechaInicio();
            LocalDate fechaFin = requestData.getFechaFin();

            if (estado == null || valservi == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA022");
                err.put("errors", "Datos incompletos: estado, numnomina y ctaor son obligatorios.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (valservi.equals("2")) {
                String sql;
                Query query;

                if ("TODAS".equals(estado)) {

                    sql = """
                                SELECT 
                                    plexa_ide_desti,
                                    plexa_nom_desti,
                                    plexa_cod_ctade,
                                    plexa_val_trans,
                                    plexa_fec_carga,
                                    COALESCE(plexa_cod_ifina, plexa_cod_etcptec) AS plexa_cod_ifina,
                                    plexa_num_plnex,
                                    COALESCE(ifi.ifina_nom_ifina, etc.etcptec_des_entid) AS ifina_nom_ifina,
                                    plexa_fec_aprob,
                                    plexa_cod_ctrnomna,
                                    plexa_cod_ctaor,
                                    plexa_tip_trans
                                FROM andplexa
                                LEFT JOIN cnxifina ifi ON ifi.ifina_cod_ifina = plexa_cod_ifina
                                LEFT JOIN andetcptec etc ON etc.etcptec_cod_etcptec = plexa_cod_etcptec
                                WHERE plexa_ide_clien = :ideclien
                            """;

                    query = entityManager.createNativeQuery(sql);
                    query.setParameter("ideclien", clienIdenti);

                } else {

                    sql = """
                                SELECT 
                                    plexa_ide_desti,
                                    plexa_nom_desti,
                                    plexa_cod_ctade,
                                    plexa_val_trans,
                                    plexa_fec_carga,
                                    COALESCE(plexa_cod_ifina, plexa_cod_etcptec) AS plexa_cod_ifina,
                                    plexa_num_plnex,
                                    COALESCE(ifi.ifina_nom_ifina, etc.etcptec_des_entid) AS ifina_nom_ifina,
                                    plexa_fec_aprob,
                                    plexa_cod_ctrnomna,
                                    plexa_cod_ctaor,
                                    plexa_tip_trans
                                FROM andplexa
                                LEFT JOIN cnxifina ifi ON ifi.ifina_cod_ifina = plexa_cod_ifina
                                LEFT JOIN andetcptec etc ON etc.etcptec_cod_etcptec = plexa_cod_etcptec
                                WHERE plexa_ide_clien = :ideclien
                                  AND plexa_cod_ctrnomna = :estado
                            """;

                    query = entityManager.createNativeQuery(sql);
                    query.setParameter("ideclien", clienIdenti);
                    query.setParameter("estado", estado);
                }

                List<Object[]> results = query.getResultList();


                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "NOMINA404");
                    err.put("errors", "No existen registros de nómina para los filtros enviados.");
                    response.put("success", false);
                    response.put("AllData", List.of(err));
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }

                int i = 1;


                for (Object[] row : results) {
                    Map<String, Object> datos = new HashMap<>();

                    datos.put("registros", i++);

                    datos.put("fec_carga",
                            row[4] != null ? row[4].toString().trim() : null);
                    datos.put("cod_banco",
                            row[5] != null ? row[5].toString().trim() : null);
                    datos.put("num_nomina",
                            row[6] != null ? row[6].toString().trim() : null);
                    datos.put("nom_banco",
                            row[7] != null ? row[7].toString().trim() : null);
                    datos.put("fec_aprobado",
                            row[8] != null ? row[8].toString().trim() : "N/A");
                    String desEstado = null;
                    if (row[9] != null) {
                        String ctrTrans = row[9].toString().trim();
                        if ("1".equals(ctrTrans)) {
                            desEstado = "PENDIENTE";
                        } else if ("0".equals(ctrTrans)) {
                            desEstado = "APROBADO";
                        } else if ("3".equals(ctrTrans)) {
                            desEstado = "NO PROCESADA";
                        } else {
                            desEstado = "DESCONOCIDO";
                        }
                    }
                    datos.put("des_estado", desEstado);
                    datos.put("cuenta_origen",
                            row[10] != null ? row[10].toString().trim() : null);
                    datos.put("estado",
                            row[9] != null ? row[9].toString().trim() : null);
                    datos.put("tip_trans",
                            row[11] != null ? row[11].toString().trim() : null);
                    allDataList.add(datos);
                }

            } else {
                String sql;
                Query query;

                if ("TODAS".equals(estado)) {
                    sql = """
                                SELECT 
                                    plina_fec_carga,   
                                    plina_fec_aprob,  
                                    plina_cod_empre,  
                                    plina_cod_ctade, 
                                    plina_num_plina,  
                                    plina_cod_ctaor,
                                    em.empre_nom_empre, 
                                    plina_ctr_trans
                                FROM andplina 
                                JOIN cnxempre em ON em.empre_cod_empre = plina_cod_empre 
                                WHERE plina_ide_clien = :ideclien
                            """;

                    query = entityManager.createNativeQuery(sql);
                    query.setParameter("ideclien", clienIdenti);

                } else {
                    sql = """
                                SELECT 
                                    plina_fec_carga,   
                                    plina_fec_aprob,  
                                    plina_cod_empre,  
                                    plina_cod_ctade, 
                                    plina_num_plina,  
                                    plina_cod_ctaor,
                                    em.empre_nom_empre, 
                                    plina_ctr_trans
                                FROM andplina 
                                JOIN cnxempre em ON em.empre_cod_empre = plina_cod_empre 
                                WHERE plina_ide_clien = :ideclien
                                  AND plina_ctr_trans = :estado
                            """;

                    query = entityManager.createNativeQuery(sql);
                    query.setParameter("ideclien", clienIdenti);
                    query.setParameter("estado", estado);
                }

                List<Object[]> results = query.getResultList();


                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "NOMINA404");
                    err.put("errors", "No existen registros de nómina para los filtros enviados.");
                    response.put("success", false);
                    response.put("AllData", List.of(err));
                    return new ResponseEntity<>(response, HttpStatus.OK);
                }

                int i = 1;

                for (Object[] row : results) {
                    Map<String, Object> datos = new HashMap<>();

                    datos.put("registros", i++);
                    datos.put("fec_carga", row[0] != null ? row[0].toString().trim() : null);
                    datos.put("fec_aprobado", row[1] != null ? row[1].toString().trim() : "N/A");
                    //    datos.put("ctadp_acredita", row[3] != null ? row[3].toString().trim() : null);
                    datos.put("num_nomina", row[4] != null ? row[4].toString().trim() : null);
                    datos.put("cuenta_origen", row[5] != null ? row[5].toString().trim() : null);

                    datos.put("nom_banco", row[6] != null ? row[6].toString().trim() : null);
                    //   datos.put("ide_cliente", row[2] != null ? row[2].toString().trim() : "N/A");

                    datos.put("estado", row[7] != null ? row[7].toString().trim() : null);
                    String desEstado = null;
                    if (row[7] != null) {
                        String ctrTrans = row[7].toString().trim();
                        if ("1".equals(ctrTrans)) {
                            desEstado = "PENDIENTE";
                        } else if ("0".equals(ctrTrans)) {
                            desEstado = "APROBADO";
                        } else if ("3".equals(ctrTrans)) {
                            desEstado = "NO PROCESADA";
                        } else {
                            desEstado = "DESCONOCIDO";
                        }
                    }
                    datos.put("des_estado", desEstado);

                    allDataList.add(datos);
                }

            }
            response.put("success", true);
            response.put("DatosNomina", allDataList);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {

            Map<String, Object> err = new HashMap<>();
            err.put("status", "ERRORNOMINA500");
            err.put("errors", e.getMessage());

            response.put("AllData", List.of(err));
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    public ResponseEntity<Map<String, Object>> cargaNominaInterna(HttpServletRequest request, Authentication authentication, List<NominasUtils> requestDataList) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {
            // 1. TOKEN DESDE COOKIE
            String token = Obtenertoken.desdeCookie(request);
            String cliacUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (token == null || authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "Sesión inválida o token no encontrado.");
                return new ResponseEntity<>(err, HttpStatus.UNAUTHORIZED);
            }

            if (cliacUsuVirtu == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos");
                response.put("status", "AA7294");
                response.put("error", "ERROR EN LA AUTENTICACIÓN");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            String ctaOrigen = requestDataList.get(0).getCtaOrigen();

            String sql1 = """
                        SELECT MAX(plina_num_plina) AS numsecu
                        FROM andplina
                        WHERE plina_cod_ctaor = :ctaOrigen
                    """;

            Query query1 = entityManager.createNativeQuery(sql1);
            query1.setParameter("ctaOrigen", ctaOrigen);

            Object result = query1.getSingleResult();

            int numSecu = (result != null ? ((Number) result).intValue() + 1 : 1);

            if (numSecu < 0) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA022");
                err.put("errors", "Error al obtener el número de secuencia interna.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String valservi = requestDataList.get(0).getValservi();

            if (!"1".equals(valservi)) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA023");
                err.put("errors", "No se puede cargar nóminas externas.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }
            // [kguanoluisa] - Se cambia ctadp_cod_depos para permitir tipos IN (1,9) - 08/05/2026
            String sqlofici = """
                        SELECT ctadp_cod_ofici FROM cnxctadp
                        WHERE ctadp_cod_ctadp = :ctaOrigen AND ctadp_cod_ectad = :estcuenta AND ctadp_cod_depos IN (1,9)
                    """;

            Query queryofi = entityManager.createNativeQuery(sqlofici);
            queryofi.setParameter("ctaOrigen", ctaOrigen);
            queryofi.setParameter("estcuenta", 1);

            List<?> resultsofi = queryofi.getResultList();

            if (resultsofi.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA022");
                err.put("errors", "Error al obtener el código de oficina.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            int codOfici = ((Number) resultsofi.get(0)).intValue();

            int i = 1;
            for (NominasUtils item : requestDataList) {

                String ideClien = item.getIdeClien();
                String ctaDestino = item.getCtaDestino();


                String descripcion = item.getDescripcion();
                String monto = item.getMonto();

                // VALIDACIÓN DE DATOS DE CADA OBJETO
                if (ideClien == null || ctaDestino == null || monto == null || ctaOrigen == null || descripcion == null) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("message", "Datos del socio incompletos");
                    err.put("status", "AA022");
                    err.put("error", "ERROR EN ENVIO DE DATOS");
                    allDataList.add(err);
                    continue;
                }


                String sql =
                        "SELECT TRIM(cl.clien_ape_clien) || ' ' || TRIM(cl.clien_nom_clien) AS nombres, " +
                                "ctadp_cod_clien, ctadp_cod_ofici, ctadp_sal_dispo " +
                                "FROM cnxctadp " +
                                "JOIN cnxclien cl ON cl.clien_cod_clien = ctadp_cod_clien " +
                                "                 AND cl.clien_ide_clien = :clienIdenti " +
                                "WHERE ctadp_cod_ctadp = :ctaDestino";

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("clienIdenti", ideClien);
                query.setParameter("ctaDestino", ctaDestino);

                List<Object[]> results = query.getResultList();

                if (results.isEmpty()) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "ERROR2002");
                    err.put("errors", "Cuenta no encontrada o no pertenece al socio.");
                    allDataList.add(err);
                    continue;
                }


                String sqlInsertPlina =
                        "INSERT INTO andplina (" +
                                "plina_cod_empre, plina_cod_ofici, plina_cod_cajas, plina_des_plina, " +
                                "plina_cod_ctaor, plina_cod_ctade, plina_val_trans, plina_usu_carga, " +
                                "plina_fec_carga, plina_usu_aprob, plina_fec_aprob, plina_num_plina, " +
                                "plina_num_trans, plina_ctr_trans, " + "plina_ide_clien) " +
                                "VALUES (:plina_cod_empre, :plina_cod_ofici, :plina_cod_cajas, :plina_des_plina, " +
                                ":plina_cod_ctaor, :plina_cod_ctade, :plina_val_trans, :plina_usu_carga, " +
                                "CURRENT, NULL, NULL, :plina_num_plina, NULL, :plina_ctr_trans , :ide_cliente)";

                Query insertPlina = entityManager.createNativeQuery(sqlInsertPlina);

                insertPlina.setParameter("plina_cod_empre", 69);
                insertPlina.setParameter("plina_cod_ofici", codOfici);
                insertPlina.setParameter("plina_cod_cajas", 803);
                insertPlina.setParameter("plina_des_plina", descripcion);
                insertPlina.setParameter("ide_cliente", clienIdenti);

                insertPlina.setParameter("plina_cod_ctaor", ctaOrigen);
                insertPlina.setParameter("plina_cod_ctade", ctaDestino);
                insertPlina.setParameter("plina_val_trans", Double.parseDouble(monto));

                insertPlina.setParameter("plina_usu_carga", cliacUsuVirtu);
                insertPlina.setParameter("plina_num_plina", numSecu);
                insertPlina.setParameter("plina_ctr_trans", 1);

                int rowInsert = insertPlina.executeUpdate();

                if (rowInsert <= 0) {
                    Map<String, Object> datos = new HashMap<>();
                    datos.put("estado", "ERROR");
                    datos.put("mensaje", "No se registró la carga de nómina");
                    allDataList.add(datos);
                    response.put("AllData", allDataList);
                } else {
                    response.put("registros", i++);
                    response.put("message", "Nomina Interna Cargada con exito");
                    response.put("success", true);
                }
            }
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            // [kguanoluisa] - Se re-lanza en lugar de retornar ResponseEntity para que el interceptor
            // @Transactional haga rollback limpio y GlobalExceptionHandler reciba la causa SQL original.
            // Si se retorna normalmente, Spring intenta commitear, ve rollback-only y lanza una NUEVA
            // UnexpectedRollbackException sin cadena de causas, perdiendo el error SQL. - 21/05/2026
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public ResponseEntity<Map<String, Object>> acreditarNominaInterna(HttpServletRequest request, Authentication authentication, List<NominasUtils> requestDataList) {


        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();


        try {
            // 1. PROCEDIMIENTO DE LOCK
            String callSetLockProcedure = "CALL cnxprc_setea_lockm()";
            Query lockProcedureQuery = entityManager.createNativeQuery(callSetLockProcedure);
            lockProcedureQuery.executeUpdate();

            String token = Obtenertoken.desdeCookie(request);

            String clienIdenti = authentication.getName();
            String cliacUsuRuc = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);


            // VALIDACIONES DE TOKEN

            if (cliacUsuRuc == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos");
                response.put("status", "AA022");

                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (!authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "La sesión no es válida o ha expirado.");
                allDataList.add(err);
                response.put("success", false);
                response.put("AllData", allDataList);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }


            // VALIDACIÓN DEL TOKEN TEMPORAL

            String sqlVerificaTokenBDD = """
                    SELECT FIRST 1 codaccess_codigo_temporal FROM vircodaccess
                    WHERE codaccess_cedula = :ced AND codaccess_usuario = :usr
                      AND codaccess_estado = '1'
                      AND codsms_codigo = '11'
                    ORDER BY codaccess_id DESC
                    """;

            Query queryVerificaTokenBDD = entityManager.createNativeQuery(sqlVerificaTokenBDD);
            queryVerificaTokenBDD.setParameter("ced", cliacUsuRuc);
            queryVerificaTokenBDD.setParameter("usr", clienIdenti);

            List<?> resultsTokenBDD = queryVerificaTokenBDD.getResultList();

            if (resultsTokenBDD.isEmpty()) {
                response.put("message", "CODIGO TEMPORAL EXPIRADO, POR EXCEDER LOS 4 MINUTOS clienIdenti");
                response.put("status", "AA027");

                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String tokenFromDB = resultsTokenBDD.get(0).toString();

            if (requestDataList.size() == 1 && "INVALIDAR_OTP_ONLY".equals(requestDataList.get(0).getPlexaTipTrans())) {
                String sqlBloqUser =
                        "UPDATE vircodaccess " +
                                "SET codaccess_estado = 0 " +
                                "WHERE codaccess_cedula = :cliacUsuRuc " +
                                "AND codaccess_usuario = :ideClieUsu " +
                                "AND codsms_codigo = :codsms_cod " +
                                "AND codaccess_codigo_temporal = :cod_sms";

                Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                resultBloqUser.setParameter("cod_sms", tokenFromDB);
                resultBloqUser.setParameter("cliacUsuRuc", cliacUsuRuc);
                resultBloqUser.setParameter("ideClieUsu", clienIdenti);
                resultBloqUser.setParameter("codsms_cod", 11);
                resultBloqUser.executeUpdate();

                response.put("message", "OTP INVALIDADO CON EXITO");
                response.put("success", true);
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

            // AQUÍ INICIA EL PROCESAMIENTO POR CADA ITEM

            for (NominasUtils dto : requestDataList) {

                String numeroCuentaEnvio = dto.getCtaOrigen();
                String valservi = dto.getValservi();
                String numeroCtaDestino = dto.getCtaDestino();
                String numNomina = dto.getNumnomina();
                String codreg = dto.getCodreg();

                if (!"1".equals(valservi)) {
                    response.put("message", "Tipo de servicio incorrecto ");
                    response.put("status", "ERROR002");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                float valTransferencia = Float.parseFloat(dto.getMonto());

                // VALIDACIONES por cada DTO

                if (numeroCuentaEnvio == null || !numeroCuentaEnvio.matches("\\d{12}")) {
                    response.put("message", "Cuenta origen inválida");
                    response.put("status", "ERROR002");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
                if (numeroCtaDestino == null || !numeroCtaDestino.matches("\\d{12}")) {
                    response.put("message", "Cuenta destino inválida");
                    response.put("status", "ERROR003");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                if (valTransferencia <= 0) {
                    response.put("message", "Valor de transferencia inválido");
                    response.put("status", "ERROR005");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                // Verificación token por cada item
                String codTempDirec = dto.getCodTempDirec();


                if (!tokenFromDB.trim().equals(dto.getCodTempDirec())) {
                    intentosRealizadoTokenFallos++;
                    if (intentosRealizadoTokenFallos >= 3) {
                        // Bloquear usuario
                        String sqlBloqUser = "UPDATE andusvco SET usvco_ctr_bloq = :bloqueo WHERE usvco_ide_clien = :rudIdenClie AND usvco_ide_usvco = :ideClieUsu";
                        Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                        resultBloqUser.setParameter("bloqueo", "0");
                        resultBloqUser.setParameter("rudIdenClie", cliacUsuRuc);
                        resultBloqUser.setParameter("ideClieUsu", clienIdenti);

                        try {
                            int rowsUpdated = resultBloqUser.executeUpdate();
                            if (rowsUpdated > 0) {
                                // Obtener datos para el correo
                                String sqlDatosCorreoIngreso = "SELECT usvco_nom_usvco, usvco_ema_usvco FROM andusvco WHERE usvco_ide_clien = :usvco_ide_clien AND usvco_ide_usvco = :usvco_ide_usvco";
                                Query resulDatosCorreoIngreso = entityManager.createNativeQuery(sqlDatosCorreoIngreso);
                                resulDatosCorreoIngreso.setParameter("usvco_ide_clien", cliacUsuRuc);
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


                                // desactivar los codigos
                                String sqlBloqCod = " UPDATE vircodaccess SET codaccess_estado = :bloqueo WHERE codaccess_usuario = :ideClieUsu AND  codaccess_cedula = :cliacUsuRuc AND codsms_codigo = :codsms ";
                                Query resultBloqcod = entityManager.createNativeQuery(sqlBloqCod);
                                resultBloqcod.setParameter("bloqueo", 0);
                                resultBloqcod.setParameter("codsms", 11);
                                resultBloqcod.setParameter("cliacUsuRuc", cliacUsuRuc);
                                resultBloqcod.setParameter("ideClieUsu", clienIdenti);
                                int filasAfectadas = resultBloqcod.executeUpdate();

                                intentosRealizadoTokenFallos = 0;
                                response.put("message", "Usuario bloqueado por exceder límite de intentos");
                                response.put("status", "AA025");
                                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                            }
                        } catch (Exception e) {
                            //kguanoluisa, [Se relanza excepcion del catch interno para propagar rollback correcto en @Transactional][][2026-05-21]
                            throw new RuntimeException("Error al intentar bloquear el usuario: " + e.getMessage(), e);
                        }
                    } else {
                        response.put("message", "Código temporal incorrecto. Intentos restantes: " + (3 - intentosRealizadoTokenFallos));
                        response.put("status", "AA023");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }
                }

                // ➤ CONSULTA SALDO

                String saldoDisponible = obtenerSaldoDisponible(numeroCuentaEnvio);
                Float saldoDispoParse = Float.parseFloat(saldoDisponible);

                if (saldoDispoParse < valTransferencia) {
                    response.put("message", "Saldo insuficiente en " + numeroCuentaEnvio);
                    response.put("status", "SALDO001");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }


                // ➤ OBTENER DATOS DE CUENTA ORIGEN / DESTINO

                String sqlCuentaOrigen = """
                        SELECT clien_cod_empre, clien_cod_ofici, ctadp_cod_ctadp
                        FROM cnxctadp, cnxclien, andusvco
                        WHERE ctadp_cod_ctadp = :cta
                        AND ctadp_cod_depos IN (1,2,9)
                        AND ctadp_cod_ectad = '1'
                        AND ctadp_cod_clien = clien_cod_clien
                        AND clien_ide_clien = usvco_ide_clien
                        """;

                Query qOri = entityManager.createNativeQuery(sqlCuentaOrigen);
                qOri.setParameter("cta", numeroCuentaEnvio);
                List<Object[]> origenList = qOri.getResultList();

                if (origenList.isEmpty()) {
                    response.put("message", "Cuenta origen no válida");
                    response.put("status", "ERROR004");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                Object[] ori = origenList.get(0);
                String codEmp = ori[0].toString().trim();
                Integer ofiOrigen = ((Number) ori[1]).intValue();
                String ctaOri = ori[2].toString().trim();


// ➤ OBTENER OFICINA DESTINO
                String sqlCuentaDestino = """
                            SELECT clien_cod_ofici
                            FROM cnxctadp, cnxclien
                            WHERE ctadp_cod_ctadp = :cta
                              AND ctadp_cod_depos IN (1,2,9)
                              AND ctadp_cod_ectad = '1'
                              AND ctadp_cod_clien = clien_cod_clien
                        """;

                Query qDes = entityManager.createNativeQuery(sqlCuentaDestino);
                qDes.setParameter("cta", numeroCtaDestino);

                Object ofiDesObj = qDes.getSingleResult();
                Integer ofiDestino = ((Number) ofiDesObj).intValue();


// DETERMINAR TIPO DE TRANSFERENCIA
                boolean mismaOficina = ofiOrigen.equals(ofiDestino);


//  SELECCIÓN DEL SP (IGUAL A GRABAR DIRECT)
                String callTransferProcedure;

                if (mismaOficina) {
                    callTransferProcedure = """
                                CALL cnxprc_reg_trfwb(:empre, :ofici, '803', :desc, :cta_ori, :cta_des, :valor)
                            """;
                } else {
                    callTransferProcedure = """
                                CALL cnxprc_trnsf_rmtwb(:empre, :ofici, '803', :desc, :cta_ori, :cta_des, :valor)
                            """;
                }


// ➤ EJECUCIÓN ÚNICA DEL SP
                Query qp = entityManager.createNativeQuery(callTransferProcedure);
                qp.setParameter("empre", codEmp);
                qp.setParameter("ofici", ofiOrigen);
                qp.setParameter("desc", "Acreditacion de nomina");
                qp.setParameter("cta_ori", ctaOri);
                qp.setParameter("cta_des", numeroCtaDestino);
                qp.setParameter("valor", valTransferencia);

                Object resultTrf = qp.getSingleResult();


                int returnValue = Integer.parseInt(resultTrf.toString());
                Integer numTrans = null;

                if (resultTrf instanceof Number) {
                    int valorSP = ((Number) resultTrf).intValue();
                    if (valorSP != -999) {
                        numTrans = valorSP;
                    }
                }

                if (numTrans < 0) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("status", "AA022");
                    err.put("errors", "Error al obtener el número de transaccion.");
                    response.put("success", false);
                    response.put("AllData", List.of(err));

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                // REGISTRO POR CADA OPERACIÓN
                Map<String, Object> reg = new HashMap<>();
                reg.put("cta_origen", numeroCuentaEnvio);
                reg.put("cta_destino", numeroCtaDestino);
                reg.put("valor", valTransferencia);
                reg.put("resultado", returnValue);
                allDataList.add(reg);


                String sqlUpdate = """
                        UPDATE andplina
                        SET plina_ctr_trans = :estado,
                            plina_usu_aprob = :usuAprob,
                            plina_fec_aprob = CURRENT,
                            plina_num_trans = :numTrans
                        WHERE plina_cod_ctaor = :ctaOrigen
                          AND plina_cod_ctade = :ctaDestino
                          AND plina_cod_plina = :codreg
                          AND plina_ctr_trans = 1
                          AND plina_num_plina = :codNomina
                        """;

                Query queryUpdate = entityManager.createNativeQuery(sqlUpdate);
                queryUpdate.setParameter("estado", 0);
                queryUpdate.setParameter("usuAprob", clienIdenti);
                queryUpdate.setParameter("numTrans", numTrans);
                queryUpdate.setParameter("ctaOrigen", numeroCuentaEnvio);
                queryUpdate.setParameter("ctaDestino", numeroCtaDestino);
                queryUpdate.setParameter("codreg", codreg);
                queryUpdate.setParameter("codNomina", numNomina);

                int updatedRow = queryUpdate.executeUpdate();
                if (updatedRow == 0) {
                    response.put("message", "Error al actualizar registros cargados.");
                    response.put("status", "AA029");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }


            }

            if (requestDataList.size() > 1) {
                String sqlBloqUser =
                        "UPDATE vircodaccess " +
                                "SET codaccess_estado = :estado " +
                                "WHERE codaccess_cedula = :cliacUsuRuc " +
                                "AND codaccess_usuario = :ideClieUsu " +
                                "AND codsms_codigo = :codsms_cod " +
                                "AND codaccess_codigo_temporal = :cod_sms";

                Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                resultBloqUser.setParameter("cod_sms", tokenFromDB);
                resultBloqUser.setParameter("estado", 0);
                resultBloqUser.setParameter("codsms_cod", 11);
                resultBloqUser.setParameter("ideClieUsu", clienIdenti);
                resultBloqUser.setParameter("cliacUsuRuc", cliacUsuRuc);

                int updatedRows = resultBloqUser.executeUpdate();

                if (updatedRows == 0) {
                    response.put("message", "No se pudo actualizar el estado del código temporal.");
                    response.put("status", "AA026");

                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
            }


            // FINALIZA LA TRANSACCIÓN
            intentosRealizadoTokenFallos = 0;
            response.put("status", "OK");
            response.put("success", true);
            response.put("message", "Nominas acreditadas correctamente");
            response.put("Registros Procesados", allDataList.size());
            response.put("AllData", allDataList);
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception ex) {
            //kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback limpio y no lanze UnexpectedRollbackException][][2026-05-21]
            throw new RuntimeException("Error en acreditarNominaInterna: " + ex.getMessage(), ex);
        }
    }

    public ResponseEntity<Map<String, Object>> genCodNomInterna(HttpServletRequest request, Authentication authentication, NominasUtils requestData) {
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

            String numeroCuentaEnvio = requestData.getCtaOrigen();
            //  String numeroCtaDestino = requestData.getCtaDestino();

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

            Libs fechaHoraService = new Libs(entityManager);
            String fecha = fechaHoraService.obtenerFechaYHora();
            System.out.println(fecha);

            String sqlQueryOrigen = "SELECT clien_cod_empre, clien_cod_ofici, ctadp_cod_ctadp, usvco_tlf_usvco, usvco_ema_usvco, clien_nom_clien, clien_ape_clien " +
                    "FROM cnxctadp, cnxclien, andusvco " +
                    "WHERE ctadp_cod_ctadp = :cod_ctadp " +
                    "AND ctadp_cod_ectad = :cod_ectad " +
                    "AND ctadp_cod_clien = :clien_cod_clien " +
                    "AND clien_ide_clien = :clien_ide_clien " +
                    "AND ctadp_cod_clien = clien_cod_clien " +
                    "AND usvco_ide_clien = clien_ide_clien " +
                    "AND usvco_tip_usvco = '1' ";

            // Consulta cuenta origen
            Query query = entityManager.createNativeQuery(sqlQueryOrigen);
            query.setParameter("cod_ctadp", numeroCuentaEnvio);
            query.setParameter("cod_ectad", "1");
            query.setParameter("clien_cod_clien", numSocio);
            query.setParameter("clien_ide_clien", clienIdenti);
            List<Object[]> results = query.getResultList();

            // Procesar resultados cuenta origen
            if (results.isEmpty()) {
                response.put("message", "Cuenta origen no encontrada, bloqueda o cerrada");
                response.put("status", "ERROR42037");
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
            smsDesbloqueo.sendSecurityCodeSMS(tlfCtaEnvio, "1150", CodigoTrfDirectas, "efectuar la Transferencia directa", fecha);
            // Enviar correo
            sendEmail enviarCorreo = new sendEmail();
            enviarCorreo.sendEmailTokenTemp(apellCtaEnvio, nombreCtaEnvio, fecha, emailCtaEnvio, CodigoTrfDirectas);

            // Actualizar estados anteriores a 0
            String sqlUpdateEstado = "UPDATE vircodaccess SET codaccess_estado = '0' WHERE codaccess_cedula = :codaccess_cedula AND codaccess_usuario = :codaccess_usuario AND codaccess_estado = '1' AND codsms_codigo = '11'";
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
            resultInsertTokenAcceso.setParameter("codsms_codigo", 11);
            resultInsertTokenAcceso.setParameter("codaccess_estado", "1");
            resultInsertTokenAcceso.setParameter("codaccess_fecha", fecha);
            resultInsertTokenAcceso.executeUpdate();
            tokenExpirationService.programarExpiracionToken(clienIdenti, CodigoTrfDirectas, "11");

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

    //CARGAR NOMINAS EXTERNAS


    public ResponseEntity<Map<String, Object>> cargaNominaExterna(HttpServletRequest request, Authentication authentication, List<NominasUtils> requestDataList) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allDataList = new ArrayList<>();

        try {
            // TOKEN
            String token = Obtenertoken.desdeCookie(request);
            String cliacUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);
            String Destransfer = "TRANSFERENCIAS INTERBANCARIAS EN LINEA";

            if (token == null || authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "Sesión inválida o token no encontrado.");
                return new ResponseEntity<>(err, HttpStatus.UNAUTHORIZED);
            }

            // VALIDACIÓN VALSERVI
            String valservi = requestDataList.get(0).getValservi();
            if (!"2".equals(valservi)) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA023");
                err.put("errors", "Tipo de servicio nóminas externas incorrecto.");
                return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
            }

            // OBTENER SECUENCIA
            String sql1 = """
                    SELECT  max(plexa_num_plnex) as numsec
                    FROM andplexa 
                    WHERE plexa_cod_ctaor = :ctaOrigen
                      AND plexa_ide_clien = :cliacUsu
                    """;

            Query query1 = entityManager.createNativeQuery(sql1);
            query1.setParameter("ctaOrigen", requestDataList.get(0).getCtaOrigen());
            query1.setParameter("cliacUsu", clienIdenti);

            Object result = query1.getSingleResult();

            int numSecu = (result != null) ? Integer.parseInt(result.toString()) + 1 : 1;

            if (numSecu <= 0) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA022");
                err.put("errors", "Error al obtener el número de secuencia externa.");
                response.put("success", false);
                response.put("AllData", List.of(err));
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }


            // OBTENER NOMBRE CLIENTE ORIGEN
            String sqlNom = """
                       SELECT TRIM(cl.clien_ape_clien) || ' ' || TRIM(cl.clien_nom_clien),
                            cl.clien_cod_ofici
                        FROM cnxctadp c
                        JOIN cnxclien cl ON cl.clien_cod_clien = c.ctadp_cod_clien
                        WHERE c.ctadp_cod_ctadp = :ctaOrigen
                          AND cl.clien_ide_clien = :identificacion
                    """;

            Query qNom = entityManager.createNativeQuery(sqlNom);
            qNom.setParameter("ctaOrigen", requestDataList.get(0).getCtaOrigen());
            qNom.setParameter("identificacion", clienIdenti);

            Object[] fila = (Object[]) qNom.getSingleResult();

            String nombresOrigen = (String) fila[0];

            String codOfici = fila[1] != null ? fila[1].toString() : null;

            // 1. Obtener Comisión Normal
            BigDecimal comisionNormal = BigDecimal.ZERO;
            try {
                String sqlComision = "SELECT comic_val_comic FROM cnxcomic " +
                        "WHERE comic_cod_comic = 5 " +
                        "AND comic_cod_ofici = :codOfici " +
                        "AND comic_cod_empre = 69";
                Query queryComision = entityManager.createNativeQuery(sqlComision);
                queryComision.setParameter("codOfici", codOfici != null ? Integer.valueOf(codOfici.trim()) : 1);
                List<?> rsComision = queryComision.getResultList();
                if (!rsComision.isEmpty() && rsComision.get(0) != null) {
                    comisionNormal = new BigDecimal(rsComision.get(0).toString().trim());
                }
            } catch (Exception e) {
                System.out.println("Error al recuperar comisión normal: " + e.getMessage());
            }

            // 2. Obtener Comisión Directa
            BigDecimal comisionDirecta = comisionNormal; // Fallback
            try {
                String sqlComisione = "SELECT cmcempr_comic_cmcempr, cmcempr_ctrl_cmcempr FROM andcmcempr " +
                        "WHERE cmcempr_ide_clien = :idclien ";
                Query queryComisione = entityManager.createNativeQuery(sqlComisione);
                queryComisione.setParameter("idclien", clienIdenti);

                List<?> rsComisione = queryComisione.getResultList();
                String ctrlComision = "0";
                BigDecimal valComisionEspecial = null;
                if (!rsComisione.isEmpty() && rsComisione.get(0) != null) {
                    Object[] filaC = (Object[]) rsComisione.get(0);
                    if (filaC[0] != null) {
                        valComisionEspecial = new BigDecimal(filaC[0].toString().trim());
                    }
                    if (filaC[1] != null) {
                        ctrlComision = filaC[1].toString().trim();
                    }
                }
                if ("1".equals(ctrlComision) && valComisionEspecial != null) {
                    comisionDirecta = valComisionEspecial;
                }
            } catch (Exception e) {
                System.out.println("Error al recuperar comisión directa: " + e.getMessage());
            }

            // 3. Calcular IVA para comisión normal
            BigDecimal totalComisionNormal = comisionNormal; // Fallback
            try {
                String sqlIva = "CALL andprc_cal_iva(69, :cuenta, :comision)";
                Query queryIva = entityManager.createNativeQuery(sqlIva);
                queryIva.setParameter("cuenta", requestDataList.get(0).getCtaOrigen().trim());
                queryIva.setParameter("comision", comisionNormal.toString());
                List<?> rsIva = queryIva.getResultList();
                if (!rsIva.isEmpty() && rsIva.get(0) != null) {
                    Object[] filaIva = (Object[]) rsIva.get(0);
                    if (filaIva[2] != null) {
                        totalComisionNormal = new BigDecimal(filaIva[2].toString().trim());
                    }
                }
            } catch (Exception e) {
                System.out.println("Error al calcular IVA normal: " + e.getMessage());
            }

            // 4. Calcular IVA para comisión directa
            BigDecimal totalComisionDirecta = comisionDirecta; // Fallback
            try {
                String sqlIva = "CALL andprc_cal_iva(69, :cuenta, :comision)";
                Query queryIva = entityManager.createNativeQuery(sqlIva);
                queryIva.setParameter("cuenta", requestDataList.get(0).getCtaOrigen().trim());
                queryIva.setParameter("comision", comisionDirecta.toString());
                List<?> rsIva = queryIva.getResultList();
                if (!rsIva.isEmpty() && rsIva.get(0) != null) {
                    Object[] filaIva = (Object[]) rsIva.get(0);
                    if (filaIva[2] != null) {
                        totalComisionDirecta = new BigDecimal(filaIva[2].toString().trim());
                    }
                }
            } catch (Exception e) {
                System.out.println("Error al calcular IVA directo: " + e.getMessage());
            }

            int i = 1;

            for (NominasUtils item : requestDataList) {

                String descripcion = item.getDescripcion();

                if (item.getIdeClien() == null || item.getCtaDestino() == null ||
                        item.getMonto() == null || item.getCtaOrigen() == null ||
                        item.getDescripcion() == null) {

                    Map<String, Object> err = new HashMap<>();
                    err.put("message", "Datos incompletos en el registro " + i);
                    err.put("success", false);
                    allDataList.add(err);
                    continue;
                }

                String sqlInsertPlexa =
                        "INSERT INTO andplexa (" +
                                "plexa_cod_empre, plexa_cod_ofici, plexa_cod_cajas, plexa_cod_cliem, plexa_cod_cliof, " +
                                "plexa_cod_clien, plexa_ide_clien, plexa_nom_clien, plexa_cod_ctaor, plexa_val_trans, " +
                                "plexa_ide_desti, plexa_nom_desti, plexa_cod_ifina, plexa_cod_ctade, plexa_cod_tcude, " +
                                "plexa_des_plexa, plexa_cod_oropi, plexa_val_comis, plexa_usu_carga, plexa_fec_carga, " +
                                "plexa_usu_aprob, plexa_fec_aprob, plexa_num_plnex, plexa_num_trans, plexa_cod_ctrnomna, " +
                                "plexa_cod_etcptec, plexa_tip_trans, plexa_tlf_desti) " +
                                "VALUES (" +
                                "69, :codOfici, 803, 69, :codOfici, :numSocio, :ideOrigen, :nomOrigen, :ctaOrigen, :valor, " +
                                ":ideDest, :nomDest, :codbanco, :ctaDest, :tcuent, :desc, 1, :valComision, :usuCarga, CURRENT, " +
                                "'', NULL, :numSecu, NULL, 1, " +
                                ":plexaCodEtcptec, :plexaTipTrans, :plexaTlfDesti)";

                Query insert = entityManager.createNativeQuery(sqlInsertPlexa);

                insert.setParameter("codOfici", codOfici);
                insert.setParameter("numSocio", numSocio);
                insert.setParameter("ideOrigen", clienIdenti);
                insert.setParameter("nomOrigen", nombresOrigen);
                insert.setParameter("ctaOrigen", item.getCtaOrigen());
                BigDecimal valor = new BigDecimal(item.getMonto().trim());
                insert.setParameter("valor", valor);
                insert.setParameter("ideDest", item.getIdeClien());
                insert.setParameter("nomDest", item.getNombresDes());
                insert.setParameter("ctaDest", item.getCtaDestino());
                insert.setParameter("desc", descripcion);
                BigDecimal valComis = (item.getPlexaCodEtcptec() != null && !item.getPlexaCodEtcptec().trim().isEmpty())
                        ? totalComisionDirecta : totalComisionNormal;
                insert.setParameter("valComision", valComis);
                insert.setParameter("usuCarga", cliacUsuVirtu);
                insert.setParameter("numSecu", numSecu);
                String codBancoVal = item.getCodbanco();
                if (item.getPlexaCodEtcptec() != null && !item.getPlexaCodEtcptec().trim().isEmpty()) {
                    codBancoVal = null;
                }
                insert.setParameter("codbanco", codBancoVal);
                insert.setParameter("tcuent", item.getTipoCuenta());
                insert.setParameter("plexaCodEtcptec", item.getPlexaCodEtcptec());
                insert.setParameter("plexaTipTrans", item.getPlexaTipTrans());
                insert.setParameter("plexaTlfDesti", item.getPlexaTlfDesti());
                insert.executeUpdate();
                i++;
            }

            Map<String, Object> resumen = new HashMap<>();
            resumen.put("registrosProcesados", i - 1);
            resumen.put("message", "Nominas Externas cargadas de manera correcta");
            allDataList.add(resumen);
            response.put("success", true);
            response.put("AllData", allDataList);

            return new ResponseEntity<>(response, HttpStatus.OK);


        } catch (Exception e) {
            //kguanoluisa, [Se cambio el retorno de ResponseEntity por throw RuntimeException para propagar el error y evitar UnexpectedRollbackException][][2026-05-21]
            throw new RuntimeException("Error en cargaNominaExterna: " + e.getMessage(), e);
        }
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<Map<String, Object>> acreditarNominaExterna(HttpServletRequest request, Authentication authentication, List<NominasUtils> requestDataList) {

        Map<String, Object> response = new HashMap<>();
        DefaultTransactionDefinition defCaptec = new DefaultTransactionDefinition();
        defCaptec.setName("REQUIRES_CAPTEC");
        defCaptec.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {


            String token = Obtenertoken.desdeCookie(request);
            String cliacUsuVirtu = authentication.getName();
            String clienIdenti = jwtUtil.getrucIdenClie(token);
            String numSocio = jwtUtil.getcodcliente(token);

            if (token == null || authentication == null || !authentication.isAuthenticated()) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA028");
                err.put("errors", "Sesión inválida o token no encontrado.");
                return new ResponseEntity<>(err, HttpStatus.UNAUTHORIZED);
            }

            if (cliacUsuVirtu == null || clienIdenti == null || numSocio == null) {
                response.put("message", "Datos del token incompletos");
                response.put("status", "AA7294");
                response.put("error", "ERROR EN LA AUTENTICACIÓN");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            if (requestDataList == null || requestDataList.isEmpty()) {
                response.put("message", "La lista de nóminas está vacía");
                response.put("status", "ERROR_EMPTY");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            if (requestDataList.size() == 1 && "INVALIDAR_OTP_ONLY".equals(requestDataList.get(0).getPlexaTipTrans())) {
                TransactionStatus statusToken = transactionManager.getTransaction(defCaptec);
                try {
                    String sqlcodTemporal =
                            "UPDATE vircodaccess SET codaccess_estado = 0 " +
                                    "WHERE codaccess_cedula = :cedula " +
                                    "AND codaccess_usuario = :usuario " +
                                    "AND codsms_codigo = 12 " +
                                    "AND codaccess_codigo_temporal = :token";

                    Query qUpd = entityManager.createNativeQuery(sqlcodTemporal);
                    qUpd.setParameter("cedula", clienIdenti);
                    qUpd.setParameter("usuario", cliacUsuVirtu);
                    qUpd.setParameter("token", requestDataList.get(0).getCodTempExter());
                    qUpd.executeUpdate();
                    transactionManager.commit(statusToken);
                } catch (Exception ex) {
                    transactionManager.rollback(statusToken);
                    throw ex;
                }
                response.put("message", "OTP INVALIDADO CON EXITO");
                response.put("status", "DTROK0005");
                return new ResponseEntity<>(response, HttpStatus.OK);
            }

            String sqlBloqueoUsuario = """
                        SELECT usvco_ctr_bloq
                        FROM andusvco
                        WHERE usvco_ide_clien = :clienIdenti
                          AND usvco_ide_usvco = :cliacUsuVirtu
                    """;

            Query queryBloq = entityManager.createNativeQuery(sqlBloqueoUsuario);
            queryBloq.setParameter("clienIdenti", clienIdenti);
            queryBloq.setParameter("cliacUsuVirtu", cliacUsuVirtu);

            List<?> resultBloq = queryBloq.getResultList();

            if (resultBloq.isEmpty()) {
                response.put("message", "Usuario no encontrado");
                response.put("status", false);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }

            int estadoBloqueo = Integer.parseInt(resultBloq.get(0).toString());

            if (estadoBloqueo != 1) {
                response.put("message", "El usuario se encuentra bloqueado");
                response.put("status", false);
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }
            // VALIDACIÓN VALSERVI

            if (!"2".equals(requestDataList.get(0).getValservi())) {
                Map<String, Object> err = new HashMap<>();
                err.put("status", "AA023");
                err.put("errors", "Tipo de servicio nóminas externas incorrecto.");
                return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
            }


            String sqlVerificaTokenBDD =
                    "SELECT codaccess_codigo_temporal " +
                            "FROM vircodaccess " +
                            "WHERE codaccess_cedula = :cedula " +
                            "AND codaccess_usuario = :usuario " +
                            "AND codaccess_estado = 1 " +
                            "AND codsms_codigo = '12'";

            Query qToken = entityManager.createNativeQuery(sqlVerificaTokenBDD);
            qToken.setParameter("cedula", clienIdenti);
            qToken.setParameter("usuario", cliacUsuVirtu);

            List<String> tokens = qToken.getResultList();
            if (tokens.isEmpty()) {
                response.put("message", "CODIGO TEMPORAL EXPIRADO, POR EXCEDER LOS 4 MINUTOS");
                response.put("status", "AA1879");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            String tokenFromDB = tokens.get(0).trim();

            if (!tokenFromDB.equals(requestDataList.get(0).getCodTempExter())) {
                intentosRealizadoTokenFallos++;
                if (intentosRealizadoTokenFallos >= 3) {
                    TransactionStatus statusLock = transactionManager.getTransaction(defCaptec);
                    try {
                        // Bloquear usuario
                        String sqlBloqUser = "UPDATE andusvco SET usvco_ctr_bloq = :bloqueo WHERE usvco_ide_clien = :ideClieUsu AND usvco_ide_usvco = :rudIdenClie";
                        Query resultBloqUser = entityManager.createNativeQuery(sqlBloqUser);
                        resultBloqUser.setParameter("bloqueo", "0");
                        resultBloqUser.setParameter("rudIdenClie", cliacUsuVirtu);
                        resultBloqUser.setParameter("ideClieUsu", clienIdenti);

                        int rowsUpdated = resultBloqUser.executeUpdate();
                        if (rowsUpdated > 0) {
                            // Obtener datos para el correo
                            String sqlDatosCorreoIngreso = "SELECT usvco_nom_usvco, usvco_ema_usvco FROM andusvco WHERE usvco_ide_clien = :usvco_ide_clien AND usvco_ide_usvco = :usvco_ide_usvco";
                            Query resulDatosCorreoIngreso = entityManager.createNativeQuery(sqlDatosCorreoIngreso);
                            resulDatosCorreoIngreso.setParameter("usvco_ide_clien", clienIdenti);
                            resulDatosCorreoIngreso.setParameter("usvco_ide_usvco", cliacUsuVirtu);
                            Libs fechaHoraService = new Libs(entityManager);
                            String FechaHora = fechaHoraService.obtenerFechaYHora();

                            List<Object[]> results2 = resulDatosCorreoIngreso.getResultList();

                            for (Object[] row2 : results2) {
                                String clienNombres = row2[0].toString().trim();
                                String clienEmail = row2[1].toString().trim();
                                String IpIngreso = requestDataList.get(0).getIpterminal();
                                sendEmail emailBloq = new sendEmail();
                                emailBloq.sendEmailBloqueo("", clienNombres, FechaHora, clienEmail, IpIngreso);
                            }

                            // desactivar los codigos
                            String sqlBloqCod = " UPDATE vircodaccess SET codaccess_estado = :bloqueo WHERE codaccess_usuario = :ideClieUsu AND  codaccess_cedula = :cliacUsuRuc AND codsms_codigo = :codsms ";
                            Query resultBloqcod = entityManager.createNativeQuery(sqlBloqCod);
                            resultBloqcod.setParameter("bloqueo", 0);
                            resultBloqcod.setParameter("codsms", 12);
                            resultBloqcod.setParameter("cliacUsuRuc", clienIdenti);
                            resultBloqcod.setParameter("ideClieUsu", cliacUsuVirtu);
                            resultBloqcod.executeUpdate();

                            intentosRealizadoTokenFallos = 0;
                        }
                        transactionManager.commit(statusLock);
                    } catch (Exception e) {
                        transactionManager.rollback(statusLock);
                        response.put("message", "Error al intentar bloquear el usuario");
                        response.put("status", "AA024");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }
                    response.put("message", "Usuario bloqueado por exceder límite de intentos");
                    response.put("status", "AA025");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                } else {
                    response.put("message", "Código temporal incorrecto. Intentos restantes: " + (3 - intentosRealizadoTokenFallos));
                    response.put("status", "AA023");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
            }

            for (NominasUtils dto : requestDataList) {

                // ==================== FASE 1: LECTURAS (sin transacción activa) ====================
                // Las consultas SELECT se ejecutan sin transacción para no generar bloqueos en Informix.
                // Esto permite que la pasarela CAPTEC consulte la misma cuenta sin conflictos.
                String numeroCuentaEnvio = dto.getCtaOrigen();
                String numeroCtaDestino = dto.getCtaDestino();
                String descripcionTrf = dto.getDescripcion();
                BigDecimal valTransferencia = dto.getValTransfer();

                // Obtener informacion para destino interbancario
                String clieIdBancoRecibe = dto.getCodbanco().trim();
                String titulaCtaRecibe = dto.getNombresBenef().trim();
                String cedulaCtaRecibe = dto.getCedulaBenef().trim();
                Integer tipoctabce = dto.getTipoCuenta();

                // CONSULTA CUENTA ORIGEN
                String sqlQuery = """
                            SELECT ctadp_cod_empre,
                                   ctadp_cod_ofici,
                                   clien_ape_clien,
                                   clien_nom_clien,
                                   ctadp_cod_clien,
                                   clien_ide_clien
                            FROM cnxctadp, cnxclien
                            WHERE ctadp_cod_ctadp = :ctadp_cod_ctadp
                              AND ctadp_cod_clien = :ctadp_cod_clien
                              AND ctadp_cod_ectad = '1'
                              AND ctadp_cod_clien = clien_cod_clien
                        """;

                Query query = entityManager.createNativeQuery(sqlQuery);
                query.setParameter("ctadp_cod_ctadp", numeroCuentaEnvio);
                query.setParameter("ctadp_cod_clien", numSocio);

                List<Object[]> results = query.getResultList();

                if (results.isEmpty()) {
                    response.put("message", "Cuenta origen no encontrada, no activa o no pertenece al socio perteneciente a esta cuenta!");
                    response.put("status", "ERROR8017");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }
                // DATOS CUENTA ORIGEN
                Object[] resultEnvio = results.get(0);

                String clieCodEmpresaEnvio = resultEnvio[0].toString().trim();
                String clienCodOficiEnvio = resultEnvio[1].toString().trim();
                String clienApellEnvio = resultEnvio[2].toString().trim();
                String clieNomEnvio = resultEnvio[3].toString().trim();
                String clienCodEnvio = resultEnvio[4].toString().trim();
                String clinIdenEnvio = resultEnvio[5].toString().trim();

                String nomApellido = clienApellEnvio + " " + clieNomEnvio;

                BigDecimal valComision = null;
                String ctrlComision = "0";

                if ("1".equals(dto.getPlexaTipTrans())) {
                    String sqlComisione = "SELECT cmcempr_comic_cmcempr, cmcempr_ctrl_cmcempr FROM andcmcempr " +
                                                 "WHERE cmcempr_ide_clien = :idclien ";
                    try {
                        Query queryComisione = entityManager.createNativeQuery(sqlComisione);
                        queryComisione.setParameter("idclien", clinIdenEnvio);
                        List<?> rsComisione = queryComisione.getResultList();
                        if (!rsComisione.isEmpty() && rsComisione.get(0) != null) {
                            Object[] fila = (Object[]) rsComisione.get(0);
                            if (fila[0] != null) {
                                valComision = new BigDecimal(fila[0].toString().trim());
                            }
                            if (fila[1] != null) {
                                ctrlComision = fila[1].toString().trim();
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar comision especial: " + e.getMessage());
                    }
                }

                if (ctrlComision.equals("0")) {
                    try {
                        String sqlComision = "SELECT comic_val_comic FROM cnxcomic " +
                                             "WHERE comic_cod_comic = 5 " +
                                             "AND comic_cod_ofici = :codOfici " +
                                             "AND comic_cod_empre = :codEmpre";
                        Query queryComision = entityManager.createNativeQuery(sqlComision);
                        queryComision.setParameter("codOfici", Integer.valueOf(clienCodOficiEnvio));
                        queryComision.setParameter("codEmpre", Integer.valueOf(clieCodEmpresaEnvio));
                        List<?> rsComision = queryComision.getResultList();
                        if (!rsComision.isEmpty() && rsComision.get(0) != null) {
                            valComision = new BigDecimal(rsComision.get(0).toString().trim());
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar comisión normal: " + e.getMessage());
                    }
                }

                if (valComision == null) {
                    response.put("message", "Comisión no configurada en la base de datos.");
                    response.put("status", "ERROR_CONFIG_COMISION_INCOMPLETA");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                // Se usa la comisión real transaccionada (valComision de andcmcempr).
                // No se llama a andprc_cal_iva porque ese SP ignora el parámetro y devuelve
                // la comisión estándar del canal, sobreescribiendo el valor correcto.
                BigDecimal totalComision = valComision;

                String saldoDisponible = obtenerSaldoDisponible(numeroCuentaEnvio);
                BigDecimal saldoDispoParse = new BigDecimal(saldoDisponible);
                BigDecimal valorsumado = valTransferencia.add(totalComision).setScale(2, RoundingMode.HALF_UP);

                if (saldoDispoParse.compareTo(valorsumado) < 0) {
                    response.put("message", "MONTO INSUFICIENTE PARA REALIZAR LA TRANSFERENCIA ");
                    response.put("error", "ERROR105");
                    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                }

                Integer numTrans = null;
                // Variables compartidas entre FASE 2 y FASE 3 para CAPTEC
                apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.BankTransferResponse gatewayResponse = null;
                BigDecimal valComisionCaptec = valComision;

                if ("1".equals(dto.getPlexaTipTrans())) {
                    // ====== FLUJO TRANSFERENCIA DIRECTA (CAPTEC) ======
                    // ==================== LECTURAS CAPTEC (sin transacción) ====================
                    // 1. Obtener datos del banco destino
                    String destFiCode = "";
                    String destAba = "";
                    try {
                        String sqlDestBank = "SELECT etcptec_cod_recept, etcptec_cod_ababin FROM andetcptec WHERE etcptec_cod_etcptec = :codbanco AND etcptec_ctr_habil = 1";
                        Query queryDestBank = entityManager.createNativeQuery(sqlDestBank);
                        queryDestBank.setParameter("codbanco", clieIdBancoRecibe);
                        List<Object[]> rsDest = queryDestBank.getResultList();
                        if (!rsDest.isEmpty() && rsDest.get(0) != null) {
                            destFiCode = rsDest.get(0)[0] != null ? rsDest.get(0)[0].toString().trim() : "";
                            destAba = rsDest.get(0)[1] != null ? rsDest.get(0)[1].toString().trim() : "";
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar datos del banco destino: " + e.getMessage());
                    }

                    // 2. Obtener systemid
                    String systemIdStr = null;
                    try {
                        String sqlSys = "SELECT sistecap_cod_sistecap FROM andsistecap " +
                                        "WHERE sistecap_ctrl_habil = 1 AND UPPER(sistecap_abrev_sistecap) IN ('VREM', 'VRES')";
                        Query qSys = entityManager.createNativeQuery(sqlSys);
                        List<?> rsSys = qSys.getResultList();
                        if (!rsSys.isEmpty()) {
                            systemIdStr = rsSys.get(0).toString().trim();
                        } else {
                            sqlSys = "SELECT sistecap_cod_sistecap FROM andsistecap WHERE sistecap_ctrl_habil = 1 AND UPPER(sistecap_abrev_sistecap) = 'VRPS'";
                            qSys = entityManager.createNativeQuery(sqlSys);
                            rsSys = qSys.getResultList();
                            if (!rsSys.isEmpty()) {
                                systemIdStr = rsSys.get(0).toString().trim();
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar systemid: " + e.getMessage());
                    }

                    if (systemIdStr == null || systemIdStr.isEmpty()) {
                        response.put("message", "Configuración de systemid no encontrada o inactiva en la base de datos.");
                        response.put("status", "ERROR_CONFIG_SYSTEMID_INCOMPLETA");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }

                    // 3. Obtener configuración de CAPTEC
                    String entityIdVal = null;
                    String originNetworkVal = null;
                    String terminalIdVal = null;
                    String sourceAbaVal = "260517";
                    String sourceFiCodeVal = "0547";
                    try {
                        String sqlCaptec = "SELECT captec_entity_id, captec_orgn_netw, captec_terminal_id, captec_aba_captec, captec_ficode_captec " +
                                           "FROM andcaptec " +
                                           "WHERE captec_cod_empre = 69 AND captec_ctrl_captec = 1";
                        Query queryCaptec = entityManager.createNativeQuery(sqlCaptec);
                        List<Object[]> rsCaptec = queryCaptec.getResultList();
                        if (!rsCaptec.isEmpty() && rsCaptec.get(0) != null) {
                            Object[] rowCaptec = rsCaptec.get(0);
                            entityIdVal = rowCaptec[0] != null ? rowCaptec[0].toString().trim() : null;
                            originNetworkVal = rowCaptec[1] != null ? rowCaptec[1].toString().trim() : null;
                            terminalIdVal = rowCaptec[2] != null ? rowCaptec[2].toString().trim() : null;
                            if (rowCaptec.length > 3 && rowCaptec[3] != null) {
                                sourceAbaVal = rowCaptec[3].toString().trim();
                            }
                            if (rowCaptec.length > 4 && rowCaptec[4] != null) {
                                sourceFiCodeVal = rowCaptec[4].toString().trim();
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar datos de andcaptec: " + e.getMessage());
                    }

                    if (entityIdVal == null || entityIdVal.isEmpty() ||
                        originNetworkVal == null || originNetworkVal.isEmpty() ||
                        terminalIdVal == null || terminalIdVal.isEmpty()) {
                        response.put("message", "Configuración de pasarela CAPTEC no encontrada o incompleta en la base de datos.");
                        response.put("status", "ERROR_CONFIG_CAPTEC_INCOMPLETA");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }

                    // 4. Obtener correo, teléfono y depósito del ordenante
                    String clientEmail = "";
                    String clientCellphone = "";
                    Integer clientCodDepos = 1;
                    try {
                        String sqlEmailPhone = "SELECT FIRST 1 usvco_ema_usvco, usvco_tlf_usvco, ctadp_cod_depos " +
                                               "FROM andusvco, cnxctadp " +
                                               "WHERE usvco_ide_clien = :clienIdenti AND usvco_tip_usvco = '1' " +
                                               "AND ctadp_cod_ctadp = :ctaEnvio";
                        Query queryEmailPhone = entityManager.createNativeQuery(sqlEmailPhone);
                        queryEmailPhone.setParameter("clienIdenti", clienIdenti);
                        queryEmailPhone.setParameter("ctaEnvio", numeroCuentaEnvio);
                        List<Object[]> rsEP = queryEmailPhone.getResultList();
                        if (!rsEP.isEmpty() && rsEP.get(0) != null) {
                            clientEmail = rsEP.get(0)[0] != null ? rsEP.get(0)[0].toString().trim() : "";
                            clientCellphone = rsEP.get(0)[1] != null ? rsEP.get(0)[1].toString().trim() : "";
                            clientCodDepos = rsEP.get(0)[2] != null ? Integer.valueOf(rsEP.get(0)[2].toString().trim()) : 1;
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar email/phone/deposito del ordenante: " + e.getMessage());
                    }



                    // 6. Moneda
                    String isoCurrency = "USD";
                    try {
                        String sqlMoneda = "SELECT moned_sgn_moned FROM cnxmoned " +
                                           "WHERE moned_cod_empre = :codEmpre " +
                                           "AND moned_cod_ofici = :codOfi " +
                                           "AND moned_cod_moned = 2";
                        Query queryMoneda = entityManager.createNativeQuery(sqlMoneda);
                        queryMoneda.setParameter("codEmpre", Integer.valueOf(clieCodEmpresaEnvio));
                        queryMoneda.setParameter("codOfi", Integer.valueOf(clienCodOficiEnvio));
                        List<?> rsMoneda = queryMoneda.getResultList();
                        if (!rsMoneda.isEmpty() && rsMoneda.get(0) != null) {
                            String sgnMoned = rsMoneda.get(0).toString().trim();
                            if (sgnMoned.equals("USD$") || sgnMoned.contains("USD")) {
                                isoCurrency = "USD";
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar moneda: " + e.getMessage());
                    }

                    // 6b. Obtener oficinaNombre
                    String oficinaNombre = "QUITO";
                    try {
                        String sqlOfi = "SELECT ofici_nom_ofici FROM cnxofici WHERE ofici_cod_ofici = :codOfi";
                        Query qOfi = entityManager.createNativeQuery(sqlOfi);
                        qOfi.setParameter("codOfi", Integer.valueOf(clienCodOficiEnvio));
                        List<?> rsOfi = qOfi.getResultList();
                        if (!rsOfi.isEmpty() && rsOfi.get(0) != null) {
                            String fullOfiName = rsOfi.get(0).toString().trim().toUpperCase();
                            oficinaNombre = fullOfiName;
                            if (fullOfiName.startsWith("OFICINA ")) {
                                String afterOficina = fullOfiName.substring(8).trim();
                                String[] parts = afterOficina.split("\\s+");
                                if (parts.length > 0) {
                                    oficinaNombre = parts[0];
                                }
                            }
                            if (fullOfiName.contains("QUITO")) {
                                oficinaNombre = "QUITO";
                            } else if (fullOfiName.contains("IBARRA")) {
                                oficinaNombre = "IBARRA";
                            } else if (fullOfiName.contains("OTAVALO")) {
                                oficinaNombre = "OTAVALO";
                            } else if (fullOfiName.contains("LATACUNGA")) {
                                oficinaNombre = "LATACUNGA";
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error al recuperar nombre de oficina: " + e.getMessage());
                    }

                    // ==================== FASE 2: LLAMADA HTTP CAPTEC (sin transacción activa) ====================
                    // No hay bloqueos en Informix durante esta llamada HTTP.
                    // CAPTEC puede consultar la cuenta origen libremente.
                    // 7. Construir DTO y llamar a executeTransfer
                    String sourceIdentType = clinIdenEnvio.length() == 13 ? "20"
                            : (clinIdenEnvio.length() == 10 ? "10" : "30");
                    String sourceAccountType = (clientCodDepos == 9) ? "20" : "10";

                    apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.AccountDTO sourceAccount = apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.AccountDTO.builder()
                            .identificationNumber(clinIdenEnvio)
                            .identificationType(sourceIdentType)
                            .accountType(sourceAccountType)
                            .accountNumber(numeroCuentaEnvio)
                            .accountHolder(nomApellido.length() > 31 ? nomApellido.substring(0, 31).trim() : nomApellido)
                            .cellphone(clientCellphone)
                            .email(clientEmail)
                            .fiCode(sourceFiCodeVal)
                            .aba(sourceAbaVal)
                            .build();

                    String destIdentType = cedulaCtaRecibe.length() == 13 ? "20"
                            : (cedulaCtaRecibe.length() == 10 ? "10" : "30");
                    String destAccountType = (tipoctabce != null && tipoctabce == 2) ? "20" : "10";

                    apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.AccountDTO destinationAccount = apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.AccountDTO.builder()
                            .identificationNumber(cedulaCtaRecibe)
                            .identificationType(destIdentType)
                            .accountType(destAccountType)
                            .accountNumber(numeroCtaDestino)
                            .accountHolder(titulaCtaRecibe.length() > 31 ? titulaCtaRecibe.substring(0, 31).trim() : titulaCtaRecibe)
                            .cellphone(dto.getPlexaTlfDesti())
                            .fiCode(destFiCode)
                            .aba(destAba)
                            .build();

                    String truncatedDesc = descripcionTrf != null ? (descripcionTrf.length() > 16 ? descripcionTrf.substring(0, 16).trim() : descripcionTrf) : "";
                    java.util.Date now = metodoPagoClientService.obtenerFechaHoraBD();
                    String finalTxDate = metodoPagoClientService.generateTxDate(now);
                    String finalTxId = metodoPagoClientService.generateTxId(now);

                    apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.BankTransferRequest gatewayDto = apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.BankTransferRequest.builder()
                            .entityId(entityIdVal)
                            .originNetwork(originNetworkVal)
                            .terminalId(terminalIdVal)
                            .txDate(finalTxDate)
                            .txId(finalTxId)
                            .systemid(systemIdStr)
                            .txtcaja("803")
                            .sourceAccount(sourceAccount)
                            .destinationAccount(destinationAccount)
                            .amount(valTransferencia)
                            .comission(valComision)
                            .currency(isoCurrency)
                            .description(truncatedDesc)
                            .city(oficinaNombre)
                            .endToEndId(System.currentTimeMillis() + "-" + dto.getCodreg())
                            .build();

                    gatewayResponse = metodoPagoClientService.executeTransfer(gatewayDto);
                    if (gatewayResponse == null || (gatewayResponse.getResult() == null && (gatewayResponse.getStatus() == null || !"000".equals(gatewayResponse.getStatus().getCode())))) {
                        String errMsg = "Error al ejecutar la transferencia en la pasarela de pagos.";
                        if (gatewayResponse != null && gatewayResponse.getStatus() != null) {
                            errMsg = gatewayResponse.getStatus().getDescription() != null && !gatewayResponse.getStatus().getDescription().trim().isEmpty()
                                     ? gatewayResponse.getStatus().getDescription()
                                     : gatewayResponse.getStatus().getMessage();
                        }
                        // Sin rollback - no hay transacción activa en esta fase
                        marcarComoNoProcesada(dto.getCodreg(), cedulaCtaRecibe, cliacUsuVirtu, clienIdenti, errMsg, defCaptec);
                        response.put("message", errMsg);
                        response.put("status", "ERROR_EJECUCION_PASARELA");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }

                    valComisionCaptec = valComision;
                } // Fin FASE 1 y 2 para CAPTEC (lecturas + HTTP)

                // ==================== FASE 3: ESCRITURAS (transacción REQUIRES_CAPTEC) ====================
                // Se inicia una transacción física independiente (PROPAGATION_REQUIRES_NEW).
                // Al hacer commit, los bloqueos se liberan INMEDIATAMENTE antes de la siguiente iteración.
                TransactionStatus status = transactionManager.getTransaction(defCaptec);
                try {
                    if ("1".equals(dto.getPlexaTipTrans())) {
                        // Registrar contable CAPTEC
                        ResponseEntity<Map<String, Object>> grabar2Response = grabar2(clieCodEmpresaEnvio, clienCodOficiEnvio, clinIdenEnvio,
                                "0", "803", valComisionCaptec.doubleValue(), 1, nomApellido, "0", "0", numeroCuentaEnvio,
                                15, "125");

                        if (grabar2Response.getStatusCode() != HttpStatus.OK) {
                            transactionManager.rollback(status);
                            marcarComoNoProcesada(dto.getCodreg(), cedulaCtaRecibe, cliacUsuVirtu, clienIdenti, "Error al registrar contabilidad CAPTEC.", defCaptec);
                            return grabar2Response;
                        }

                        try {
                            if (gatewayResponse.getResult() != null && gatewayResponse.getResult().getNumttran() != null) {
                                numTrans = Integer.parseInt(gatewayResponse.getResult().getNumttran().trim());
                            }
                        } catch (Exception ex) {
                            System.out.println("No se pudo parsear el número de transacción (numttran) a Integer: " + ex.getMessage());
                        }

                    } else {
                        // ====== FLUJO TRANSFERENCIA SPI NORMAL ======
                        // LLAMADA SP SPI
                        String callTransferProcedure =
                                "CALL cnxprc_reg_spi01_wb(" +
                                        ":clienCodEmpreEnvio," +
                                        ":clienCodOficiEnvio,'803'," +
                                        ":clienCodEmpreEnvio," +
                                        ":clienCodOficiEnvio," +
                                        ":clienCodEnvio," +
                                        ":clinIdenEnvio," +
                                        ":nomApellido," +
                                        ":numeroCuentaEnvio," +
                                        ":valTransferencia," +
                                        ":cedulaCtaRecibe," +
                                        ":titulaCtaRecibe," +
                                        ":clieIdBancoRecibe," +
                                        ":numeroCtaDestino," +
                                        ":tipoctabce," +
                                        "'TRANSFERENCIAS INTERBANCARIAS EN LINEA'," +
                                        "1,:valComisionProc)";

                        Query queryProcedure = entityManager.createNativeQuery(callTransferProcedure);
                        queryProcedure.setParameter("clienCodEmpreEnvio", clieCodEmpresaEnvio);
                        queryProcedure.setParameter("clienCodOficiEnvio", clienCodOficiEnvio);
                        queryProcedure.setParameter("clienCodEnvio", clienCodEnvio);
                        queryProcedure.setParameter("clinIdenEnvio", clinIdenEnvio);
                        queryProcedure.setParameter("nomApellido", nomApellido);
                        queryProcedure.setParameter("numeroCuentaEnvio", numeroCuentaEnvio);
                        queryProcedure.setParameter("valTransferencia", valTransferencia);
                        queryProcedure.setParameter("cedulaCtaRecibe", cedulaCtaRecibe);
                        queryProcedure.setParameter("titulaCtaRecibe", titulaCtaRecibe);
                        queryProcedure.setParameter("clieIdBancoRecibe", clieIdBancoRecibe);
                        queryProcedure.setParameter("numeroCtaDestino", numeroCtaDestino);
                        queryProcedure.setParameter("tipoctabce", tipoctabce);
                        queryProcedure.setParameter("valComisionProc", totalComision.toString());

                        Object result = queryProcedure.getSingleResult();
                        int returnValue = Integer.parseInt(result.toString());

                        numTrans = (result != null && Integer.parseInt(result.toString().trim()) != -999) ? Integer.parseInt(result.toString().trim()) : null;

                        // REGISTRO CONTABLE
                        double valComisionDouble = valComision.doubleValue();
                        ResponseEntity<Map<String, Object>> grabar2Response = grabar2(clieCodEmpresaEnvio, clienCodOficiEnvio, clinIdenEnvio,
                                "0", "803", valComisionDouble, 1, nomApellido, "0", "0", numeroCuentaEnvio,
                                15, "125");

                        if (grabar2Response.getStatusCode() != HttpStatus.OK) {
                            transactionManager.rollback(status);
                            marcarComoNoProcesada(dto.getCodreg(), cedulaCtaRecibe, cliacUsuVirtu, clienIdenti, "Error al registrar contabilidad SPI.", defCaptec);
                            return grabar2Response;
                        }
                    }

                    //cambiar estado de nomina y guardar la comision realmente transaccionada
                    String sqlUpdatePlexa = """
                                UPDATE andplexa
                                SET plexa_cod_ctrnomna = :estado, 
                                    plexa_fec_aprob = CURRENT, 
                                    plexa_usu_aprob = :usu_aprob, 
                                    plexa_num_trans = :numTrans,
                                    plexa_val_comis = :valComis
                                WHERE plexa_cod_plexa = :codPlexa
                                  AND plexa_ide_desti = :ideDesti
                                  AND plexa_cod_cajas = 803
                                  AND plexa_ide_clien = :ideClien
                            """;

                    Query updateQuery = entityManager.createNativeQuery(sqlUpdatePlexa);
                    updateQuery.setParameter("estado", 0);
                    updateQuery.setParameter("ideDesti", cedulaCtaRecibe);
                    updateQuery.setParameter("codPlexa", dto.getCodreg());
                    updateQuery.setParameter("usu_aprob", cliacUsuVirtu);
                    updateQuery.setParameter("numTrans", numTrans);
                    updateQuery.setParameter("ideClien", clienIdenti);
                    updateQuery.setParameter("valComis", totalComision);

                    int filasActualizadas = updateQuery.executeUpdate();

                    if (filasActualizadas <= 0) {
                        transactionManager.rollback(status);
                        marcarComoNoProcesada(dto.getCodreg(), cedulaCtaRecibe, cliacUsuVirtu, clienIdenti, "No se pudo actualizar el estado a exitoso.", defCaptec);
                        response.put("message", "No se pudo actualizar el estado de la transferencia.");
                        response.put("status", "AA022");
                        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
                    }

                    // COMMIT REQUIRES_CAPTEC: libera todos los bloqueos de escritura inmediatamente
                    transactionManager.commit(status);
                } catch (Exception ex) {
                    transactionManager.rollback(status);
                    marcarComoNoProcesada(dto.getCodreg(), cedulaCtaRecibe, cliacUsuVirtu, clienIdenti, ex.getMessage(), defCaptec);
                    throw ex;
                }
            }


            if (requestDataList.size() > 1) {
                //  INVALIDAR OTP (UNA SOLA VEZ)
                TransactionStatus statusToken = transactionManager.getTransaction(defCaptec);
                try {
                    String sqlcodTemporal =
                            "UPDATE vircodaccess SET codaccess_estado = 0 " +
                                    "WHERE codaccess_cedula = :cedula " +
                                    "AND codaccess_usuario = :usuario " +
                                    "AND codsms_codigo = 12 " +
                                    "AND codaccess_codigo_temporal = :token";

                    Query qUpd = entityManager.createNativeQuery(sqlcodTemporal);
                    qUpd.setParameter("cedula", clienIdenti);
                    qUpd.setParameter("usuario", cliacUsuVirtu);
                    qUpd.setParameter("token", requestDataList.get(0).getCodTempExter());
                    qUpd.executeUpdate();
                    transactionManager.commit(statusToken);
                } catch (Exception ex) {
                    transactionManager.rollback(statusToken);
                    throw ex;
                }
            }

            intentosRealizadoTokenFallos = 0;

            response.put("message", "TRANSFERENCIAS INTERBANCARIAS REALIZADAS CON ÉXITO !!");
            response.put("status", "DTROK0005");
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            throw new RuntimeException("Error en acreditarNominaExterna: " + e.getMessage(), e);
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

            String sqlCobrarIva = "SELECT profi_val_enter FROM cnxprofi WHERE profi_cod_profi = 'ctrlcbrivasocio' AND profi_cod_ofici = :codigoOficina";
            Query queryCobrarIva = entityManager.createNativeQuery(sqlCobrarIva);
            queryCobrarIva.setParameter("codigoOficina", codigoOficina);
            List<?> rsCobrarIva = queryCobrarIva.getResultList();
            // toma el valor de la consulta 1 = si cobra, 0 = no cobra
            int cobrarIva = 1;
            if (!rsCobrarIva.isEmpty() && rsCobrarIva.get(0) != null) {
                cobrarIva = Integer.parseInt(rsCobrarIva.get(0).toString());
            }

            int codfprod = 15; // COMISIONES SERVICIOS CON IVA si no es socio
            int iva = 6;

            if (!rsSocioCliente.isEmpty() && cobrarIva == 0) {
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
            String rfcta_fec_emisi = "TODAY";

            String rfcta_num_compr = null;


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

            if (rfcta_cod_efctr.equals(1)) {
                if (modo.equals(1)) {
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
                    queryRegistraDocumentoWeb.setParameter("fecharegistro", fechaFor);
                    String resultado = (String) queryRegistraDocumentoWeb.getSingleResult();
                    System.out.println("Resultado del procedimiento: " + resultado);

                }
            }
            response.put("message", "Factura generada con éxito");
            response.put("status", "OK");
            response.put("numFactura", numrfcta);
            response.put("secuencia", nsecuencia);
            response.put("establecimiento", nsestablecimiento);
            response.put("puntoEmision", nspuntoemision);
            response.put("totalFactura", totalFactura);

            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (Exception e) {
            //kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback limpio. El metodo grabar2 hace INSERTs/CALLs contables][][2026-05-21]
            throw new RuntimeException("Error en grabar2: " + e.getMessage(), e);
        }
    }


    public ResponseEntity<Map<String, Object>> genCodNomExterna(HttpServletRequest request, Authentication authentication, NominasUtils requestData) {
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

            String numeroCuentaEnvio = requestData.getCtaOrigen();
            String numeroCtaDestino = requestData.getCtaDestino();

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
            //if (numeroCtaDestino == null || !numeroCtaDestino.matches("\\d{12}")) {
            //   response.put("message", "El número de cuenta destino debe tener exactamente 12 dígitos numéricos.");
            //    response.put("status", "ERROR58023");
            //    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            //  }
            Libs fechaHoraService = new Libs(entityManager);
            String fecha = fechaHoraService.obtenerFechaYHora();
            System.out.println(fecha);

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
            query.setParameter("clien_ide_clien", clienIdenti);
            List<Object[]> results = query.getResultList();

            //  String sqlQueryVerDestino = """
            //        SELECT * FROM cnxctadp WHERE ctadp_cod_ctadp = :cta_banco AND ctadp_cod_ectad = '1'
            //          """;
            // Consulta cuenta destino
            //   Query query1 = entityManager.createNativeQuery(sqlQueryVerDestino);
            //   query1.setParameter("cta_banco", numeroCtaDestino);
            //   List<Object[]> results1 = query1.getResultList();

            // Procesar resultados cuenta origen
            if (results.isEmpty()) {
                response.put("message", "Cuenta origen no encontrada, bloqueda o cerrada" + "numeroCuentaEnvio " + numeroCuentaEnvio + "numSocio " + numSocio + "cliacUsuRuc " + cliacUsuRuc + "clienIdenti " + clienIdenti);
                response.put("status", "ERROR42037");
                return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
            }

            // Procesar resultados cuenta destino
            //   if (results1.isEmpty()) {
            //    response.put("message", "Cuenta destino no encontrada, bloqueda o cerrada");
            //     response.put("status", "ERROR962037");
            //     return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
//}

            // Extraer datos de las cuentas
            Object[] resultEnvio = results.get(0);

            String tlfCtaEnvio = resultEnvio[3].toString().trim();
            String emailCtaEnvio = resultEnvio[4].toString().trim();
            String nombreCtaEnvio = resultEnvio[5].toString().trim();
            String apellCtaEnvio = resultEnvio[6].toString().trim();

            //generar codigo
            String CodigoTrfDirectas = codigoAleatorio6Temp();
            SendSMS smsDesbloqueo = new SendSMS();
            smsDesbloqueo.sendSecurityCodeSMS(tlfCtaEnvio, "1150", CodigoTrfDirectas, "efectuar la Transferencia directa", fecha);
            // Enviar correo
            sendEmail enviarCorreo = new sendEmail();
            enviarCorreo.sendEmailTokenTemp(apellCtaEnvio, nombreCtaEnvio, fecha, emailCtaEnvio, CodigoTrfDirectas);

            // Actualizar estados anteriores a 0
            String sqlUpdateEstado = "UPDATE vircodaccess SET codaccess_estado = '0' WHERE codaccess_cedula = :codaccess_cedula AND codaccess_usuario = :codaccess_usuario AND codaccess_estado = '1' AND codsms_codigo = 12";
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
            resultInsertTokenAcceso.setParameter("codsms_codigo", 12);
            resultInsertTokenAcceso.setParameter("codaccess_estado", "1");
            resultInsertTokenAcceso.setParameter("codaccess_fecha", fecha);
            resultInsertTokenAcceso.executeUpdate();
            tokenExpirationService.programarExpiracionToken(clienIdenti, CodigoTrfDirectas, "12");

            response.put("message", "CODIGO GENERADO CON EXITO clienIdenti");
            response.put("status", "CODTRFOK005");
            return new ResponseEntity<>(response, HttpStatus.OK);


        } catch (Exception e) {
            //kguanoluisa, [Se relanza excepcion para que @Transactional haga rollback del UPDATE e INSERT en vircodaccess][][2026-05-21]
            throw new RuntimeException("Error en genCodNomExterna: " + e.getMessage(), e);
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

    // Genera un número aleatorio de 6 dígitos
    public String codigoAleatorio6Temp() {
        Random random = new Random();
        int numeroAleatorio = 100000 + random.nextInt(900000); // Asegura 6 dígitos
        return String.valueOf(numeroAleatorio);
    }

    private float redondearMoneda(float valor) {
        return (float) (Math.floor(valor * 100 + 0.5) / 100);
    }

    private String eliminarAcentos(String input) {
        return input.replaceAll("[^\\p{ASCII}]", "");
    }

    private void marcarComoNoProcesada(String codPlexa, String ideDesti, String usuAprob, String ideClien, String errorMsg, DefaultTransactionDefinition defCaptec) {
        TransactionStatus statusError = transactionManager.getTransaction(defCaptec);
        try {
            String truncatedMsg = errorMsg != null ? (errorMsg.length() > 100 ? errorMsg.substring(0, 100).trim() : errorMsg.trim()) : "Transaccion no procesada";
            String sqlUpdatePlexaError = """
                        UPDATE andplexa
                        SET plexa_cod_ctrnomna = 3, plexa_fec_aprob = CURRENT, plexa_usu_aprob = :usu_aprob, plexa_des_desti = :errorMsg
                        WHERE plexa_cod_plexa = :codPlexa
                          AND plexa_ide_desti = :ideDesti
                          AND plexa_cod_cajas = 803
                          AND plexa_ide_clien = :ideClien
                    """;
            Query updateErrorQuery = entityManager.createNativeQuery(sqlUpdatePlexaError);
            updateErrorQuery.setParameter("codPlexa", codPlexa);
            updateErrorQuery.setParameter("ideDesti", ideDesti);
            updateErrorQuery.setParameter("usu_aprob", usuAprob);
            updateErrorQuery.setParameter("ideClien", ideClien);
            updateErrorQuery.setParameter("errorMsg", truncatedMsg);
            updateErrorQuery.executeUpdate();
            transactionManager.commit(statusError);
        } catch (Exception exVal) {
            transactionManager.rollback(statusError);
            System.out.println("No se pudo actualizar la transferencia a estado 3: " + exVal.getMessage());
        }
    }

}
