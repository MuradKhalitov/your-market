package ru.murad.yourmarket.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component @RequiredArgsConstructor
public class AdminApiKeyFilter extends OncePerRequestFilter {
    private final AdminProperties properties;
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configured = properties.apiKey();
        String supplied = request.getHeader("X-Admin-Api-Key");
        if (configured == null || configured.isBlank() || supplied == null || !MessageDigest.isEqual(
                configured.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"errorCode\":\"UNAUTHORIZED\",\"message\":\"Invalid admin API key\"}"); return;
        }
        chain.doFilter(request,response);
    }
}
