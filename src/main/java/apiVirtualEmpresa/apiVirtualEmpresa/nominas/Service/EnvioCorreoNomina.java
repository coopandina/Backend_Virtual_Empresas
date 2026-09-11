package apiVirtualEmpresa.apiVirtualEmpresa.nominas.Service;

import org.springframework.stereotype.Service;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.util.ByteArrayDataSource;
import java.util.Properties;

@Service
public class EnvioCorreoNomina {

    public void enviarCorreoConPdf(String destinatario, String asunto, String mensajeHtml, byte[] pdfBytes, String nombrePdf) throws Exception {
        System.out.println("=== START ENVIAR CORREO CON PDF ===");
        System.out.println("DESTINATARIO: [" + destinatario + "]");
        System.out.println("ASUNTO: [" + asunto + "]");
        System.out.println("PDF TAMANO: " + (pdfBytes != null ? pdfBytes.length : 0) + " bytes");

        if (destinatario == null || destinatario.trim().isEmpty()) {
            throw new IllegalArgumentException("El correo destinatario está vacío o es nulo.");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", "172.16.17.44");
        props.put("mail.smtp.port", "25");
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.starttls.enable", "false");
        props.put("mail.smtp.timeout", "6000");

        final String user = "andinaapp@coopandina.fin.ec";
        final String password = "AppAndina.2021";

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user, password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("andinaapp@coopandina.fin.ec", "Cooperativa ANDINA"));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario.trim()));
        message.setReplyTo(InternetAddress.parse("andinaapp@coopandina.fin.ec"));
        message.setSubject(asunto != null ? asunto : "Notificación - Cooperativa de Ahorro y Crédito Andina Ltda.", "UTF-8");

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(mensajeHtml, "text/html; charset=UTF-8");

        MimeBodyPart pdfPart = new MimeBodyPart();
        pdfPart.setDataHandler(new DataHandler(new ByteArrayDataSource(pdfBytes, "application/pdf")));
        pdfPart.setFileName(nombrePdf != null && !nombrePdf.isEmpty() ? nombrePdf : "comprobante.pdf");

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);
        multipart.addBodyPart(pdfPart);

        message.setContent(multipart);

        System.out.println("Enviando correo a través de Transport.send...");
        Transport.send(message);
        System.out.println("=== CORREO ENVIADO EXITOSAMENTE A " + destinatario + " ===");
    }
}

