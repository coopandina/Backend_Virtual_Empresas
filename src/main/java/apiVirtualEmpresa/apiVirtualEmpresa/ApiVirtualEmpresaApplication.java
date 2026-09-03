package apiVirtualEmpresa.apiVirtualEmpresa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication(exclude = {
        ErrorMvcAutoConfiguration.class
})
public class ApiVirtualEmpresaApplication extends SpringBootServletInitializer {

    @PostConstruct
    public void init(){
        TimeZone.setDefault(TimeZone.getTimeZone("America/Guayaquil"));
    }


    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(ApiVirtualEmpresaApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(ApiVirtualEmpresaApplication.class, args);
    }
}


