package apiVirtualEmpresa.apiVirtualEmpresa.services;

import apiVirtualEmpresa.apiVirtualEmpresa.dto.captec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Base64;

@Service
public class MetodoPagoClientService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final EntityManager entityManager;

    public MetodoPagoClientService(
            RestTemplate restTemplate,
            @Value("${metodo-pago.api.base-url}") String baseUrl,
            @Value("${metodo-pago.api.username}") String username,
            @Value("${metodo-pago.api.password}") String password,
            EntityManager entityManager) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.username = username;
        this.password = password;
        this.entityManager = entityManager;
    }

    private String truncate(String val, int maxLen) {
        if (val == null) return null;
        val = val.trim();
        return val.length() > maxLen ? val.substring(0, maxLen).trim() : val;
    }

    private String sanitizeCity(String city) {
        if (city == null) return null;
        city = city.trim().toUpperCase();
        if (city.startsWith("OFICINA ")) {
            String afterOficina = city.substring(8).trim();
            String[] parts = afterOficina.split("\\s+");
            if (parts.length > 0) {
                city = parts[0];
            }
        }
        if (city.contains("LATACUNGA")) {
            city = "LATACUNGA";
        }
        return truncate(city, 15);
    }

    private void sanitizeRequestAccounts(AccountDTO source, AccountDTO dest, boolean isVerification) {
        if (source != null) {
            source.setAccountHolder(truncate(source.getAccountHolder(), 30));
            if (source.getCellphone() == null || source.getCellphone().trim().isEmpty() || "null".equalsIgnoreCase(source.getCellphone())) {
                source.setCellphone(null);
            }
            if (isVerification) {
                source.setEmail(null);
            } else {
                if (source.getEmail() == null || source.getEmail().trim().isEmpty() || "null".equalsIgnoreCase(source.getEmail())) {
                    source.setEmail(null);
                }
            }
        }
        if (dest != null) {
            dest.setAccountHolder(truncate(dest.getAccountHolder(), 30));
            dest.setEmail(null); // Email is always null (removed) for destination account
            if (isVerification) {
                dest.setCellphone("0000000000");
            } else {
                if (dest.getCellphone() == null || dest.getCellphone().trim().isEmpty() || "null".equalsIgnoreCase(dest.getCellphone())) {
                    dest.setCellphone(null);
                }
            }
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    public ValidatedEntityResponse verifyRecipient(ValidatedEntityRequest request) {
        // Resolve dynamic source account data
        if (request.getSourceAccount() != null && request.getSourceAccount().getAccountNumber() != null) {
            String ctaEnvio = request.getSourceAccount().getAccountNumber();
            // System.out.println("=== [DATABASE DEBUG: verifyRecipient] START ===");
            // System.out.println("Input Source Account (ctaEnvio): " + ctaEnvio);

            // 1. Look up CAPTEC details
            String entityIdVal = null;
            String originNetworkVal = null;
            String terminalIdVal = null;
            String abaVal = null;
            String fiCodeVal = null;

            try {
                String sqlCaptec = "SELECT captec_entity_id, captec_orgn_netw, captec_terminal_id, captec_aba_captec, captec_ficode_captec " +
                                   "FROM andcaptec " +
                                   "WHERE captec_cod_empre = 69 AND captec_ctrl_captec = 1";
                // System.out.println("Executing query on 'andcaptec'...");
                Query queryCaptec = entityManager.createNativeQuery(sqlCaptec);
                List<Object[]> rsCaptec = queryCaptec.getResultList();
                if (!rsCaptec.isEmpty() && rsCaptec.get(0) != null) {
                    Object[] row = rsCaptec.get(0);
                    if (row[0] != null) entityIdVal = row[0].toString().trim();
                    if (row[1] != null) originNetworkVal = row[1].toString().trim();
                    if (row[2] != null) terminalIdVal = row[2].toString().trim();
                    if (row.length > 3 && row[3] != null) abaVal = row[3].toString().trim();
                    if (row.length > 4 && row[4] != null) fiCodeVal = row[4].toString().trim();
                    // System.out.println("CAPTEC details resolved successfully:");
                    // System.out.println("  entityIdVal: " + entityIdVal);
                    // System.out.println("  originNetworkVal: " + originNetworkVal);
                    // System.out.println("  terminalIdVal: " + terminalIdVal);
                    // System.out.println("  abaVal: " + abaVal);
                    // System.out.println("  fiCodeVal: " + fiCodeVal);
                } else {
                    // System.out.println("WARNING: Query on 'andcaptec' returned NO results.");
                }
            } catch (Exception e) {
                // System.out.println("Error querying captec table: " + e.getMessage());
                e.printStackTrace();
            }

            if (entityIdVal == null || originNetworkVal == null || terminalIdVal == null || abaVal == null || fiCodeVal == null) {
                // System.out.println("ERROR: Missing CAPTEC configuration in database.");
                throw new IllegalStateException("Configuracion de pasarela CAPTEC no encontrada o incompleta en la base de datos.");
            }

            request.setEntityId(entityIdVal);
            request.setOriginNetwork(originNetworkVal);
            request.setTerminalId(terminalIdVal);

            // 2. Look up Client details
            boolean clientFound = false;
            try {
                String sqlCliente = "SELECT FIRST 1 " +
                        "clien_ide_clien, " +
                        "TRIM(clien_nom_clien) || ' ' || TRIM(clien_ape_clien) AS nombre_completo, " +
                        "usvco_ema_usvco, " +
                        "usvco_tlf_usvco, " +
                        "clien_cod_empre, " +
                        "clien_cod_ofici, " +
                        "ctadp_cod_depos " +
                        "FROM cnxctadp, cnxclien, andusvco " +
                        "WHERE ctadp_cod_ctadp = :ctaEnvio " +
                        "AND ctadp_cod_depos IN (1, 9) " +
                        "AND ctadp_cod_ectad = '1' " +
                        "AND ctadp_cod_clien = clien_cod_clien " +
                        "AND clien_ide_clien = usvco_ide_clien " +
                        "AND usvco_tip_usvco = '1'";
                // System.out.println("Executing query on cnxctadp/cnxclien/andusvco...");
                Query queryCliente = entityManager.createNativeQuery(sqlCliente);
                queryCliente.setParameter("ctaEnvio", ctaEnvio);
                List<Object[]> resultClienteList = queryCliente.getResultList();
                if (!resultClienteList.isEmpty() && resultClienteList.get(0) != null) {
                    clientFound = true;
                    Object[] row = resultClienteList.get(0);
                    String clientIdentification = row[0] != null ? row[0].toString().trim() : "";
                    String clientName = row[1] != null ? row[1].toString().trim() : "";
                    String clientEmail = row[2] != null ? row[2].toString().trim() : "";
                    String clientCellphone = row[3] != null ? row[3].toString().trim() : null;
                    int clientCodOfici = row[5] != null ? Integer.parseInt(row[5].toString().trim()) : 1;
                    int clientCodDepos = row[6] != null ? Integer.parseInt(row[6].toString().trim()) : 1;

                    String sourceIdentType = clientIdentification.length() == 13 ? "20"
                            : (clientIdentification.length() == 10 ? "10" : "30");
                    String sourceAccountType = (clientCodDepos == 9) ? "20" : "10";

                    request.getSourceAccount().setIdentificationNumber(clientIdentification);
                    request.getSourceAccount().setIdentificationType(sourceIdentType);
                    request.getSourceAccount().setAccountType(sourceAccountType);
                    request.getSourceAccount().setAccountHolder(truncate(clientName, 30));
                    request.getSourceAccount().setCellphone(clientCellphone);
                    request.getSourceAccount().setEmail(clientEmail);
                    
                    // Query dynamic city from cnxofici
                    String oficinaNombre = "QUITO";
                    try {
                        String sqlOfi = "SELECT ofici_nom_ofici FROM cnxofici WHERE ofici_cod_ofici = :codOfi";
                        Query qOfi = entityManager.createNativeQuery(sqlOfi);
                        qOfi.setParameter("codOfi", clientCodOfici);
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
                        // System.out.println("Error resolving office city for verification: " + e.getMessage());
                    }
                    request.setCity(oficinaNombre);
                    
                    // System.out.println("Client details resolved successfully:");
                    // System.out.println("  clientIdentification: " + clientIdentification);
                    // System.out.println("  clientName: " + clientName);
                    // System.out.println("  clientEmail: " + clientEmail);
                    // System.out.println("  clientCellphone: " + clientCellphone);
                    // System.out.println("  sourceIdentType: " + sourceIdentType);
                    // System.out.println("  sourceAccountType: " + sourceAccountType);
                    // System.out.println("  resolvedCity: " + oficinaNombre);
                } else {
                    // System.out.println("WARNING: Query on cnxctadp/cnxclien/andusvco returned NO results.");
                }
            } catch (Exception e) {
                // System.out.println("Error querying client details for validated-entity: " + e.getMessage());
                throw new IllegalStateException("Error al consultar datos del cliente origen en la base de datos.", e);
            }

            if (!clientFound) {
                // System.out.println("ERROR: Client details not found in database.");
                throw new IllegalStateException("Datos del cliente origen no encontrados en la base de datos para la cuenta: " + ctaEnvio);
            }

            request.getSourceAccount().setFiCode(fiCodeVal);
            request.getSourceAccount().setAba(abaVal);
            // System.out.println("=== [DATABASE DEBUG: verifyRecipient] END ===");
        }

        // Resolve systemid dynamically from database and sanitize/truncate account fields
        request.setSystemid(resolveSystemId(request.getSystemid()));
        sanitizeRequestAccounts(request.getSourceAccount(), request.getDestinationAccount(), true);
        request.setCity(sanitizeCity(request.getCity()));

        String url = baseUrl + "api/ordenante/validated-entity";
        HttpHeaders headers = createHeaders();
        HttpEntity<ValidatedEntityRequest> entity = new HttpEntity<>(request, headers);
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        
        // System.out.println("=== [GATEWAY DEBUG: verifyRecipient] START ===");
        // System.out.println("URL: " + url);
        // System.out.println("Headers: " + headers);
        // try {
        //     String jsonRequest = mapper.writeValueAsString(request);
        //     System.out.println("Payload (JSON):\n" + jsonRequest);
        // } catch (Exception e) {
        //     System.out.println("Payload (String representation): " + request);
        // }

        try {
            ResponseEntity<ValidatedEntityResponse> response = restTemplate.postForEntity(url, entity, ValidatedEntityResponse.class);
            // System.out.println("Response Status Code: " + response.getStatusCode());
            // try {
            //     String jsonResponse = mapper.writeValueAsString(response.getBody());
            //     System.out.println("Response Body (JSON):\n" + jsonResponse);
            // } catch (Exception e) {
            //     System.out.println("Response Body: " + response.getBody());
            // }
            // System.out.println("=== [GATEWAY DEBUG: verifyRecipient] END ===");
            return response.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // System.out.println("Response Status Code: " + e.getStatusCode());
            String responseBody = e.getResponseBodyAsString();
            // System.out.println("Response Error Body: " + responseBody);
            // System.out.println("=== [GATEWAY DEBUG: verifyRecipient] END (WITH HTTP STATUS ERROR) ===");
            try {
                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    return mapper.readValue(responseBody, ValidatedEntityResponse.class);
                }
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Error al conectar con la pasarela de pagos (validated-entity): " + e.getMessage(), e);
        } catch (Exception e) {
            // System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            // System.out.println("=== [GATEWAY DEBUG: verifyRecipient] END (WITH EXCEPTION) ===");
            throw new RuntimeException("Error al conectar con la pasarela de pagos (validated-entity): " + e.getMessage(), e);
        }
    }

    public BankTransferResponse executeTransfer(BankTransferRequest request) {
        // Resolve systemid dynamically from database and sanitize/truncate account fields
        request.setSystemid(resolveSystemId(request.getSystemid()));
        sanitizeRequestAccounts(request.getSourceAccount(), request.getDestinationAccount(), false);
        request.setCity(sanitizeCity(request.getCity()));

        String url = baseUrl + "api/ordenante/bank-transfer";
        HttpHeaders headers = createHeaders();
        HttpEntity<BankTransferRequest> entity = new HttpEntity<>(request, headers);
        
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        
        // System.out.println("=== [GATEWAY DEBUG: executeTransfer] START ===");
        // System.out.println("URL: " + url);
        // System.out.println("Headers: " + headers);
        // try {
        //     String jsonRequest = mapper.writeValueAsString(request);
        //     System.out.println("Payload (JSON):\n" + jsonRequest);
        // } catch (Exception e) {
        //     System.out.println("Payload (String representation): " + request);
        // }

        try {
            ResponseEntity<BankTransferResponse> response = restTemplate.postForEntity(url, entity, BankTransferResponse.class);
            // System.out.println("Response Status Code: " + response.getStatusCode());
            // try {
            //     String jsonResponse = mapper.writeValueAsString(response.getBody());
            //     System.out.println("Response Body (JSON):\n" + jsonResponse);
            // } catch (Exception e) {
            //     System.out.println("Response Body: " + response.getBody());
            // }
            // System.out.println("=== [GATEWAY DEBUG: executeTransfer] END ===");
            return response.getBody();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // System.out.println("Response Status Code: " + e.getStatusCode());
            String responseBody = e.getResponseBodyAsString();
            // System.out.println("Response Error Body: " + responseBody);
            // System.out.println("=== [GATEWAY DEBUG: executeTransfer] END (WITH HTTP STATUS ERROR) ===");
            try {
                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    return mapper.readValue(responseBody, BankTransferResponse.class);
                }
            } catch (Exception ignored) {
            }
            throw new RuntimeException("Error al conectar con la pasarela de pagos (bank-transfer): " + e.getMessage(), e);
        } catch (Exception e) {
            // System.out.println("Exception: " + e.getMessage());
            e.printStackTrace();
            // System.out.println("=== [GATEWAY DEBUG: executeTransfer] END (WITH EXCEPTION) ===");
            throw new RuntimeException("Error al conectar con la pasarela de pagos (bank-transfer): " + e.getMessage(), e);
        }
    }

    private Integer resolveSystemId(Object systemid) {
        if (systemid != null) {
            try {
                return Integer.parseInt(systemid.toString().trim());
            } catch (Exception ignored) {}
        }
        
        Integer systemIdVal = 4; // Fallback default
        try {
            String sqlSys = "SELECT sistecap_cod_sistecap FROM andsistecap " +
                            "WHERE sistecap_ctrl_habil = 1 AND UPPER(sistecap_abrev_sistecap) IN ('VREM', 'VRES')";
            Query qSys = entityManager.createNativeQuery(sqlSys);
            List<?> rsSys = qSys.getResultList();
            if (!rsSys.isEmpty() && rsSys.get(0) != null) {
                systemIdVal = Integer.parseInt(rsSys.get(0).toString().trim());
            } else {
                sqlSys = "SELECT sistecap_cod_sistecap FROM andsistecap WHERE sistecap_ctrl_habil = 1 AND UPPER(sistecap_abrev_sistecap) = 'VRPS'";
                qSys = entityManager.createNativeQuery(sqlSys);
                rsSys = qSys.getResultList();
                if (!rsSys.isEmpty() && rsSys.get(0) != null) {
                    systemIdVal = Integer.parseInt(rsSys.get(0).toString().trim());
                }
            }
        } catch (Exception ignored) {}
        return systemIdVal;
    }
}
