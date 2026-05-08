package apiVirtualEmpresa.apiVirtualEmpresa.FirmaSRi.dto;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class FirmaUtils {

    private String xmlPath;
    private String pfxPath;
    private String pin;
    private String pathOut;
    private String nameFileOut;

    private String txtcodofici;
    private String txtcodedocu;
    private String dtpfecdesde;
    private String dtpfechasta;

    private String codigoEmpresa;
    private String codigoOficina;
    private String tipoComprobante;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;


    public String getTxtcodigoEmpresa() {
        return codigoEmpresa;
    }

    public void setTxtcodigoEmpresa(String txtcodigoEmpresa) {
        this.codigoEmpresa = txtcodigoEmpresa;
    }

    public String getTxtcodofici() { return txtcodofici; }
    public void setTxtcodofici(String txtcodofici) { this.txtcodofici = txtcodofici; }

    public String getTxtcodedocu() { return txtcodedocu; }
    public void setTxtcodedocu(String txtcodedocu) { this.txtcodedocu = txtcodedocu; }

    public String getDtpfecdesde() { return dtpfecdesde; }
    public void setDtpfecdesde(String dtpfecdesde) { this.dtpfecdesde = dtpfecdesde; }

    public String getDtpfechasta() { return dtpfechasta; }
    public void setDtpfechasta(String dtpfechasta) { this.dtpfechasta = dtpfechasta; }


    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DB = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private FirmaUtils() {}

    public static String leftPad(String value, int length) {
        return String.format("%1$" + length + "s", value).replace(' ', '0');
    }

    public static String fechaToDB(String fecha) {
        LocalDate date = LocalDate.parse(fecha.trim(), INPUT);
        return date.format(DB);
    }


    public String getXmlPath() {
        return xmlPath;
    }

    public void setXmlPath(String xmlPath) {
        this.xmlPath = xmlPath;
    }

    public String getPfxPath() {
        return pfxPath;
    }

    public void setPfxPath(String pfxPath) {
        this.pfxPath = pfxPath;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getPathOut() {
        return pathOut;
    }

    public void setPathOut(String pathOut) {
        this.pathOut = pathOut;
    }

    public String getNameFileOut() {
        return nameFileOut;
    }

    public void setNameFileOut(String nameFileOut) {
        this.nameFileOut = nameFileOut;
    }

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    //limpiarTextoSri

    public static String limpiarTextoSri(String texto) {

        if (texto == null || texto.trim().isEmpty()) {
            return "";
        }

        texto = texto.trim();

        texto = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");

        texto = texto.replace("ñ", "n").replace("Ñ", "N");

        return texto;
    }


    //formato fehca fechaBddXml (YYYY-MM-DD)

    public static String[] fechaBddXml(String fecha) {

        if (fecha == null || fecha.isEmpty()) {
            return new String[]{"", "", ""};
        }

        String[] f = fecha.split("-");
        String ano = f[0];
        String mes = f[1];
        String dia = f[2];

        String fecEmision = dia + "/" + mes + "/" + ano;
        String ejerFiscal = mes + "/" + ano;
        String fecSimple = dia + mes + ano;

        return new String[]{fecEmision, ejerFiscal, fecSimple};
    }


    //      Formato fecha (DDMMAAAA)

    public static String[] fechaBddXmlBdd(String fecha) {

        if (fecha == null || fecha.length() != 8) {
            return new String[]{"", "", ""};
        }

        String dia = fecha.substring(0, 2);
        String mes = fecha.substring(2, 4);
        String ano = fecha.substring(4, 8);

        String fecNormal = mes + "/" + dia + "/" + ano;
        String ejerFiscal = mes + "/" + ano;
        String fecSimple = mes + dia + ano;

        return new String[]{fecNormal, ejerFiscal, fecSimple};
    }


    //generar xml
    public static String generaXmlDocumento(int sriComprobante, String infoTributaria, String infoDocumento,
                                            String detalleDocumento, String infoAdicional, String claveAcceso) throws Exception {
        infoTributaria   = limpiarTextoSri(infoTributaria);
        infoDocumento    = limpiarTextoSri(infoDocumento);
        detalleDocumento = limpiarTextoSri(detalleDocumento);
        infoAdicional    = limpiarTextoSri(infoAdicional);


        String etiquetaDoc = "factura";
        if (sriComprobante == 7) {
            etiquetaDoc = "comprobanteRetencion";
        }


        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<").append(etiquetaDoc)
                .append(" id=\"comprobante\" version=\"1.0.0\">");
        xml.append(infoTributaria);
        xml.append(infoDocumento);
        xml.append(detalleDocumento);
        xml.append(infoAdicional);
        xml.append("</").append(etiquetaDoc).append(">");


        String nombreArchivo = claveAcceso + ".xml";
        String ruta = "../documentos_electronicos/generados/";

        File directorio = new File(ruta);
        if (!directorio.exists()) {
            directorio.mkdirs();
        }

        File archivo = new File(ruta + nombreArchivo);

        try (FileOutputStream fos = new FileOutputStream(archivo)) {
            fos.write(xml.toString().getBytes(StandardCharsets.UTF_8));
        }

        //Retornar ruta completa
        return archivo.getAbsolutePath();
    }


    public static String stripAccents(String input) {
        if (input == null) {
            return null;
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutAccents = DIACRITICS.matcher(normalized).replaceAll("");
        return withoutAccents.replaceAll("[\\[\\^´`¨~\\]]", "");
    }

}
