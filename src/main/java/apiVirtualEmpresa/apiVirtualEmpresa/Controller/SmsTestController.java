package apiVirtualEmpresa.apiVirtualEmpresa.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sms.SendSMS;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SmsTestController {

    @GetMapping("/api/test-sms")
    public Map<String, Object> testSms(@RequestParam String clienNumero) {
        Map<String, Object> responseMap = new HashMap<>();
        
        try {
            // Formato de fecha igual al de tu proyecto
            String fechaIngresoLogin = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String tokenTemp = "123456"; // Token de prueba

            SendSMS smsCodigoTemp = new SendSMS();
            String resultSMS = smsCodigoTemp.sendSecurityCodeSMS(clienNumero, "1150", tokenTemp, "Iniciar Sesion", fechaIngresoLogin);

            responseMap.put("status", "SUCCESS");
            responseMap.put("celular", clienNumero);
            responseMap.put("fecha", fechaIngresoLogin);
            responseMap.put("token_enviado", tokenTemp);
            responseMap.put("respuesta_eclipsoft", resultSMS);

        } catch (Exception e) {
            responseMap.put("status", "ERROR");
            responseMap.put("mensaje", e.getMessage());
        }

        return responseMap;
    }
}
