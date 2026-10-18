package com.booking.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a per-request correlation id for logging and clients.
 * <p>
 * Reads {@value #REQUEST_ID_HEADER} when present; otherwise generates a UUID. The id is stored in SLF4J {@link MDC}
 * under {@value #MDC_REQUEST_ID_KEY} for structured JSON logs and echoed on the response as
 * {@value #REQUEST_ID_HEADER}. MDC is cleared after the request completes.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter implements Ordered {

    /** HTTP header used for inbound and outbound request correlation. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** MDC key included in Logstash JSON output ({@code logback-spring.xml}). */
    public static final String MDC_REQUEST_ID_KEY = "requestId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID_KEY);
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }
}
