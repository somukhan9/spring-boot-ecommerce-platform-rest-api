package com.shop.common.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JsonErrors {
    private JsonErrors() {}

    /** message must be a constant (no user input) - it is written without JSON escaping. */
    public static void write(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"status\":" + status + ",\"error\":\"" + HttpStatus.valueOf(status).getReasonPhrase()
                + "\",\"message\":\"" + message + "\"}";
        res.getWriter().write(body);
    }
}
