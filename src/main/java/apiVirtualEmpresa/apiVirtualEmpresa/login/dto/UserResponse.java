package apiVirtualEmpresa.apiVirtualEmpresa.login.dto;

public class UserResponse {
    private String username;

    public UserResponse(String username) {
        this.username = username;
    }

    public String getUsername() { return username; }
}