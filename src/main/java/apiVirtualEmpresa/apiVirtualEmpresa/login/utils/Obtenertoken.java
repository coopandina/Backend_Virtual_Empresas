package apiVirtualEmpresa.apiVirtualEmpresa.login.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public class Obtenertoken {

    public static String desdeCookie(HttpServletRequest request) {

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie c : request.getCookies()) {
            if ("jwt".equals(c.getName())) {
                return c.getValue();
            }
        }

        return null;
    }
}
