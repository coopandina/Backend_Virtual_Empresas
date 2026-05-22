package apiVirtualEmpresa.apiVirtualEmpresa.FirmaSRi.Service;

import apiVirtualEmpresa.apiVirtualEmpresa.FirmaSRi.dto.FirmaUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import srijava.XAdESBESSignature;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional
@Service
public class FirmaService {
    private final JdbcTemplate jdbcTemplate;

    public FirmaService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Autowired
    private EntityManager entityManager;


    public ResponseEntity<Map<String, Object>> firmarArchivo(FirmaUtils request) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> allData = new ArrayList<>();

        try {
            String xmlPath = request.getXmlPath();
            String pfxPath = request.getPfxPath();
            String pin = request.getPin();
            String pathOut = request.getPathOut();
            String nameFileOut = request.getNameFileOut();

            Map<String, Object> item = new HashMap<>();

            if (xmlPath == null || pfxPath == null || pin == null
                    || pathOut == null || nameFileOut == null) {

                item.put("success", false);
                item.put("message", "Faltan parámetros obligatorios");
                item.put("path", null);

                allData.add(item);
                response.put("AllData", allData);

                return ResponseEntity.badRequest().body(response);
            }

            XAdESBESSignature.firmar(
                    xmlPath,
                    pfxPath,
                    pin,
                    pathOut,
                    nameFileOut
            );

            item.put("success", true);
            item.put("message", "Archivo firmado correctamente.");
            item.put("path", pathOut + nameFileOut);

            allData.add(item);
            response.put("AllData", allData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            Map<String, Object> item = new HashMap<>();
            item.put("success", false);
            item.put("message", "Error al firmar el archivo: " + e.getMessage());
            item.put("path", null);

            allData.add(item);
            response.put("AllData", allData);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }

    public Map<String, Object> obtenerClavesAcceso(FirmaUtils dto) {

        String codOfici = FirmaUtils.leftPad(dto.getTxtcodofici(), 3);
        String tipoComprobante = dto.getTxtcodedocu();

        String fechaDesde = FirmaUtils.fechaToDB(dto.getDtpfecdesde());
        String fechaHasta = FirmaUtils.fechaToDB(dto.getDtpfechasta());

        String sql = """
                    SELECT *
                    FROM ecerdcwb
                    WHERE rdcwb_cod_tcomp = ?
                      AND rdcwb_sec_estab = ?
                      AND rdcwb_cod_edcwb <> 2
                      AND rdcwb_cod_edcwb NOT IN (99)
                      AND rdcwb_fec_regis >= ?
                      AND rdcwb_fec_regis <= ?
                    ORDER BY rdcwb_sec_estab, rdcwb_sec_pemis, rdcwb_num_cmprt DESC
                """;

        List<Map<String, Object>> data = jdbcTemplate.queryForList(
                sql,
                tipoComprobante,
                codOfici,
                fechaDesde,
                fechaHasta
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", true);
        response.put("total", data.size());
        response.put("data", data);

        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void grabarDocumento(FirmaUtils request) {

        String tipoComprobante = request.getTxtcodedocu();
        String DIR = "../documentos_electronicos";

        try {

            procesarNoValidados(
                    DIR + "/rptWebNoValidados.txt",
                    tipoComprobante
            );

            procesarNoAutorizados(
                    DIR + "/rptWebNoAutorizados.txt",
                    tipoComprobante
            );

            procesarAutorizados(
                    DIR + "/rptWebAutorizados.txt",
                    tipoComprobante
            );

        } catch (Exception e) {

            throw new RuntimeException("Error al grabar documentos electrónicos", e);
        }
    }

    private void procesarNoValidados(String ruta, String tipoComprobante) throws Exception {

        List<String> lineas = Files.readAllLines(Paths.get(ruta));

        for (String linea : lineas) {

            String[] data = linea.split("\\|");

            String clave = data[0].trim();
            String estado = data[1].trim();
            String mensaje = data[2].trim().toUpperCase();

            String estab = clave.substring(24, 27);
            String pemis = clave.substring(27, 30);
            String sec = clave.substring(30, 39);

            int ctrop = estado.equals("DEVUELTA") ? 4 : 1;

            jdbcTemplate.update("""
                        UPDATE ecerdcwb
                           SET rdcwb_cod_edcwb = ?,
                               rdcwb_msj_rdcwb = ?
                         WHERE rdcwb_sec_estab = ?
                           AND rdcwb_sec_pemis = ?
                           AND rdcwb_num_cmprt = ?
                           AND rdcwb_cod_tcomp = ?
                    """, ctrop, mensaje, estab, pemis, sec, tipoComprobante);
        }
    }

    private void procesarNoAutorizados(String ruta, String tipoComprobante) throws Exception {

        List<String> lineas = Files.readAllLines(Paths.get(ruta));

        for (String linea : lineas) {

            String[] data = linea.split("\\|");

            String clave = data[0].trim();
            String estado = data[1].trim();
            String mensaje = data[2].trim().toUpperCase();

            String estab = clave.substring(24, 27);
            String pemis = clave.substring(27, 30);
            String sec = clave.substring(30, 39);

            int ctrop = estado.equals("NO AUTORIZADO") ? 3 : 1;

            jdbcTemplate.update("""
                        UPDATE ecerdcwb
                           SET rdcwb_cod_edcwb = ?,
                               rdcwb_msj_rdcwb = ?
                         WHERE rdcwb_sec_estab = ?
                           AND rdcwb_sec_pemis = ?
                           AND rdcwb_num_cmprt = ?
                           AND rdcwb_cod_tcomp = ?
                    """, ctrop, mensaje, estab, pemis, sec, tipoComprobante);
        }
    }

    private void procesarAutorizados(String ruta, String tipoComprobante) throws Exception {

        List<String> lineas = Files.readAllLines(Paths.get(ruta));

        for (String linea : lineas) {

            String[] data = linea.split("\\|");

            String clave = data[0].trim();
            String estado = data[1].trim();
            String numAutor = data[2].trim();
            String fechaAutor = data[3].trim();

            String fecha = fechaAutor.substring(0, 10);
            String hora = fechaAutor.substring(11, 19);
            String fechaHora = fecha + " " + hora;

            String fechaAutorDB = FirmaUtils.fechaToDB(fecha);
            String fechaDoc = clave.substring(0, 8);
            String[] fecEmision = FirmaUtils.fechaBddXmlBdd(fechaDoc);

            String estab = clave.substring(24, 27);
            String pemis = clave.substring(27, 30);
            String sec = clave.substring(30, 39);

            ;
            String sriComprobante = descripcioncampo("ecetcomp", "tcomp_cod_tcomp", tipoComprobante, "tcomp_cod_tcsri", "");
            int sriComp = Integer.parseInt(sriComprobante);

            int ctrop = "AUTORIZADO".equals(estado) ? 2 : 1;

            jdbcTemplate.update("""
                               UPDATE ecerdcwb
                                  SET rdcwb_cod_edcwb = ?,
                                      rdcwb_num_autor = ?,
                                      rdcwb_fec_autor = ?,
                                      rdcwb_clv_acces = ?,
                                      rdcwb_msj_rdcwb = ''
                                WHERE rdcwb_sec_estab = ?
                                  AND rdcwb_sec_pemis = ?
                                  AND rdcwb_num_cmprt = ?
                                  AND rdcwb_cod_tcomp = ?
                            """, ctrop, numAutor, fechaHora, clave,
                    estab, pemis, sec, tipoComprobante);
            if (sriComp == 1) {
                jdbcTemplate.update(
                        "UPDATE ecerfcta SET rfcta_num_autor = ?, rfcta_fec_autor = ?, rfcta_clv_acces = ? " +
                                "WHERE rfcta_sec_estab = ? AND rfcta_sec_pemis = ? AND rfcta_num_rfcta = ? " +
                                "AND rfcta_fec_emisi = ? AND rfcta_cod_tcomp = ?",
                        numAutor, fechaAutorDB, clave, estab, pemis, sec, fecEmision, tipoComprobante);
            }
            if (sriComp == 7) {
                String secuencial10 = sec;
                if (sec.length() < 10) {
                    secuencial10 = String.format("%010d", Integer.parseInt(sec));
                }
                jdbcTemplate.update(
                        "UPDATE cnxcmprt SET cmprt_num_autor = ?, cmprt_fec_autor = ? " +
                                "WHERE cmprt_nse_ofprv = ? AND cmprt_nse_cjprv = ? " +
                                "AND (cmprt_num_cmprt = ? OR cmprt_num_cmprt = ?) " +
                                "AND cmprt_fec_reten = ?",
                        numAutor, fechaAutorDB, estab, pemis, secuencial10, sec, fecEmision);
            }


        }
    }

    //descripcioncampo

    public String descripcioncampo(String tabla, String codigo, String valcod, String nombre, String where) {

        String sql = "SELECT " + nombre +
                " FROM " + tabla +
                " WHERE " + codigo + " = ? " +
                (where != null ? where : "");

        List<String> resultados = jdbcTemplate.query(sql, new Object[]{valcod}, (rs, rowNum) -> rs.getString(nombre));

        return resultados.isEmpty()
                ? "?"
                : resultados.get(0).trim();
    }


    public Map<String, Object> srtProcesa(FirmaUtils request) {

        Map<String, Object> response = new HashMap<>();
        // 1. Datos de entrada

        String codigoEmpresa = request.getTxtcodigoEmpresa();
        String codigoOficina = request.getTxtcodofici();
        String tipoComprobante = request.getTxtcodedocu();

        LocalDate fechaDesde = LocalDate.parse(request.getDtpfecdesde(),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));

        LocalDate fechaHasta = LocalDate.parse(request.getDtpfechasta(),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));

        LocalDate fd = LocalDate.parse("01/31/2025",
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));

        LocalDate fh = LocalDate.parse("12/31/2025",
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));


        int nRow = 0;
        String sqlRdcwb =
                "SELECT * " +
                        "FROM ecerdcwb " +
                        "WHERE rdcwb_cod_tcomp = :tipoComprobante " +
                        "AND rdcwb_sec_estab = :codigoOficina " +
                        "AND rdcwb_cod_edcwb <> 2 " +
                        "AND rdcwb_cod_edcwb NOT IN (99) " +
                        "AND rdcwb_fec_regis >= :fechaDesde " +
                        "AND rdcwb_fec_regis <= :fechaHasta " +
                        "ORDER BY rdcwb_sec_estab, rdcwb_sec_pemis, rdcwb_num_cmprt DESC";


        Query queryRdcwb = entityManager.createNativeQuery(sqlRdcwb);

        queryRdcwb.setParameter("tipoComprobante", tipoComprobante);
        queryRdcwb.setParameter("codigoOficina", codigoOficina);
        queryRdcwb.setParameter("fechaDesde", fd);
        queryRdcwb.setParameter("fechaHasta", fh);

        List<Object[]> rsRdcwb = queryRdcwb.getResultList();

        if (rsRdcwb == null || rsRdcwb.isEmpty()) {
            // no hay registros
            Map<String, Object> alldata = new HashMap<>();
            alldata.put("status", false);
            alldata.put("fechaDesde", fd);
            alldata.put("oficina", codigoOficina);
            alldata.put("tipoComprobante", tipoComprobante);
            alldata.put("fechaHasta", fh);
            alldata.put("mensaje", "No se encontro registros");

            response.put("alldata", alldata);

            return response;
        } else {
            // hay registros
        }

        // =====================
        // 2. Consultas BD
        // =====================
        // procesarNoValidados(...)
        // procesarNoAutorizados(...)
        // procesarAutorizados(...)

        // =====================
        // 3. Generar XML
        // =====================
        // FirmaUtils.generaXmlDocumento(...)

        // =====================
        // 4. Firmar
        // =====================
        // XAdESBESSignature.firmar(...)
        Map<String, Object> alldata = new HashMap<>();
        alldata.put("status", true);
        alldata.put("fechaDesde", fechaDesde);
        alldata.put("oficina", codigoOficina);
        alldata.put("tipoComprobante", tipoComprobante);
        alldata.put("fechaHasta", fechaHasta);
        alldata.put("mensaje", "Proceso ejecutado correctamente");

        response.put("alldata", alldata);

        return response;
    }

}