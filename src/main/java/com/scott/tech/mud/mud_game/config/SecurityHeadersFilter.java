package com.scott.tech.mud.mud_game.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String STYLESHEET_SWAP_ONLOAD_HASH = "'sha256-MhtPZXr7+LpJUY5qtMutB+qWfQtMaPccfe7QXtCcEYc='";
    private static final Pattern IMMUTABLE_ASSET_PATH = Pattern.compile("^/.+-[A-Za-z0-9]{8,}\\.(?:js|css)$");
    private static final Set<String> NO_CACHE_PATHS = Set.of("/", "/index.html", "/app-init.js");
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self' 'unsafe-hashes' " + STYLESHEET_SWAP_ONLOAD_HASH,
            "script-src-attr 'unsafe-hashes' " + STYLESHEET_SWAP_ONLOAD_HASH,
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src 'self' ws: wss:",
            "object-src 'none'",
            "base-uri 'self'",
            "frame-ancestors 'none'",
            "form-action 'self'"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");

        if (isSecureRequest(request)) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000");
        }

        filterChain.doFilter(request, response);
        applyCacheHeaders(request, response);
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }

        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && forwardedProto.equalsIgnoreCase("https")) {
            return true;
        }

        String forwarded = request.getHeader("Forwarded");
        return forwarded != null && forwarded.toLowerCase(Locale.ROOT).contains("proto=https");
    }

    private void applyCacheHeaders(HttpServletRequest request, HttpServletResponse response) {
        if (response.containsHeader("Cache-Control")) {
            return;
        }

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return;
        }

        int status = response.getStatus();
        if (status < 200 || status >= 400) {
            return;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (path == null || path.isBlank()) {
            return;
        }
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if (IMMUTABLE_ASSET_PATH.matcher(path).matches()) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            return;
        }

        if (NO_CACHE_PATHS.contains(path)) {
            response.setHeader("Cache-Control", "no-cache");
        }
    }
}
