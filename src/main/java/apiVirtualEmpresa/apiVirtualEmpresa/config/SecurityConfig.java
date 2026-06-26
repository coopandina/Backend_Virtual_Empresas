package apiVirtualEmpresa.apiVirtualEmpresa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**",
                                "/api/firma-sri/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // ✅ Agregar tu IP del frontend con puerto 3000
        configuration.setAllowedOrigins(Arrays.asList(
            "http://192.168.17.109:5174",
            "http://192.168.17.109:3000/",
            "http://192.168.17.109:4000/",
                "http://192.168.17.72:5174",
                "http://192.168.17.72:3000/",
                "http://192.168.17.72:4000/",
                "http://192.168.17.31",
                "http://172.16.17.27",
                "http://192.168.17.156",
                "http://172.16.17.24",
                "http://172.1.0.134",
                "https://172.1.0.134",
                "http://172.16.17.27:8080/VirtualEmpresas/",
                "http://172.16.17.33:8080/VirtualEmpresas/",
                "http://172.16.17.33/VirtualEmpresas/",
                "https://virtualcoop.coopandina.fin.ec:80",
                "https://virtualcoop.coopandina.fin.ec",
                "https://virtualcoop.coopandina.fin.ec:8080",
                "https://andinadigital.coopandina.fin.ec",
                "https://andinadigital.coopandina.fin.ec:80",
                "https://andinadigital.coopandina.fin.ec:8080",
                "https://andinadigital.coopandina.fin.ec:4173",
                "https://andinadigital.coopandina.fin.ec:4173/empresas",
                "https://andinadigital.coopandina.fin.ec/empresas",
                "https://digital.coopandina.fin.ec",
                "https://digital.coopandina.fin.ec:80",
                "https://digital.coopandina.fin.ec:8080",
                "https://digital.coopandina.fin.ec:4173",
                "https://digital.coopandina.fin.ec:4173/empresas",
                "https://digital.coopandina.fin.ec/empresas",
                "https://digital.coopandina.fin.ec:4173/empresas",
                "https://digital.coopandina.fin.ec/empresas",
                "https://digital.coopandina.fin.ec:4173/empresas"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}