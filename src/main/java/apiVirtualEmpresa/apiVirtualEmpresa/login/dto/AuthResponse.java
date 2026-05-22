package apiVirtualEmpresa.apiVirtualEmpresa.login.dto;

public class AuthResponse {
    private String message;
    private String username;

    public AuthResponse(String message, String username) {
        this.message = message;
        this.username = username;
    }

    // Getters
    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }
}