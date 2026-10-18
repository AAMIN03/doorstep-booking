package com.booking.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Writes one JSON log line for availability, booking create, booking read, and cancel. The line includes the request id
 * already stored in MDC by {@link RequestIdFilter}.
 */
@Component
public class ApiAccessLogFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessLogFilter.class);
    private static final int MAX_BODY_CHARS = 8192;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !appliesTo(request.getMethod(), request.getRequestURI());
    }

    /**
     * @return whether this method and path are create, get, cancel, or availability
     */
    static boolean appliesTo(String method, String uri) {
        if (method == null || uri == null) {
            return false;
        }
        if ("GET".equals(method) && "/availability".equals(uri)) {
            return true;
        }
        if ("/bookings".equals(uri) && ("GET".equals(method) || "POST".equals(method))) {
            return true;
        }
        if ("GET".equals(method) && uri.matches("/bookings/\\d+")) {
            return true;
        }
        return "POST".equals(method) && uri.matches("/bookings/\\d+/cancel");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, MAX_BODY_CHARS);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            try {
                logAccess(requestWrapper, responseWrapper);
            } finally {
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    private static void logAccess(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        Charset charset = charset(request.getCharacterEncoding());
        String requestBody = "GET".equals(request.getMethod()) ? "" : body(request.getContentAsByteArray(), charset);
        log.info("http access", StructuredArguments.kv("httpMethod", request.getMethod()),
                StructuredArguments.kv("path", request.getRequestURI()),
                StructuredArguments.kv("query", request.getQueryString() == null ? "" : request.getQueryString()),
                StructuredArguments.kv("status", response.getStatus()),
                StructuredArguments.kv("requestBody", requestBody),
                StructuredArguments.kv("responseBody", body(response.getContentAsByteArray(), charset)));
    }

    private static Charset charset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String body(byte[] content, Charset charset) {
        if (content == null || content.length == 0) {
            return "";
        }
        String text = new String(content, charset);
        if (text.length() <= MAX_BODY_CHARS) {
            return text;
        }
        return text.substring(0, MAX_BODY_CHARS);
    }
}
