package apiVirtualEmpresa.apiVirtualEmpresa.login.controller;

import apiVirtualEmpresa.apiVirtualEmpresa.config.JwtUtil;
import apiVirtualEmpresa.apiVirtualEmpresa.login.dto.UserCredentials;
import apiVirtualEmpresa.apiVirtualEmpresa.login.dto.UserResponse;
import apiVirtualEmpresa.apiVirtualEmpresa.login.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    public AuthController(JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

  /*  @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        // ... autenticación ...

        String token = jwtUtil.generateToken(request.getEmail());

        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);     // ✅ false en desarrollo (sin HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);

        // ✅ Para desarrollo en red local
        response.setHeader("Set-Cookie", String.format(
                "jwt=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                token, 24 * 60 * 60
        ));

        response.addCookie(cookie);
        return ResponseEntity.ok(new AuthResponse("Login exitoso", request.getEmail()));
    }*/

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // [kguanoluisa] - Delegar logout lógico al servicio para liberar la sesión - 12/05/2026
        return authService.logout(request, response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@CookieValue(name = "jwt", required = false) String token) {
        if (token != null && jwtUtil.validateToken(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            return ResponseEntity.ok(new UserResponse(username));
        }
        return ResponseEntity.status(401).body("No autenticado");
    }


    @PostMapping(value = "/login")
    public ResponseEntity<Map<String, Object>> accessLogin(@RequestBody UserCredentials request, HttpServletResponse response) {
        return authService.accesslogin(request, response);
    }

}
