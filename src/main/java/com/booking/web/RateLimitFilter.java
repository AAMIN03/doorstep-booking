package com.booking.web;

import com.booking.dto.ErrorResponse;
import com.booking.exception.ErrorCode;
import com.booking.ratelimit.RateLimitOperations;
import com.booking.service.TimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies a shared token-bucket limit (via {@link RateLimitOperations}) to booking API routes.
 * <p>
 * Limited paths: {@code /availability}, {@code /bookings}, {@code /technicians/{id}/schedule}. Actuator and other
 * routes are excluded. Client key is derived from {@code X-Forwarded-For} (first hop) or
 * {@link HttpServletRequest#getRemoteAddr()}. On exceed, responds with HTTP 429 and
 * {@link ErrorCode#RATE_LIMIT_EXCEEDED}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final RateLimitOperations rateLimitOperations;
    private final TimeService timeService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitOperations rateLimitOperations, TimeService timeService,
            ObjectMapper objectMapper) {
        this.rateLimitOperations = rateLimitOperations;
        this.timeService = timeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !appliesTo(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = resolveClientKey(request);
        if (!rateLimitOperations.tryConsume(clientKey)) {
            writeTooManyRequests(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * @return whether the URI is subject to rate limiting
     */
    static boolean appliesTo(String uri) {
        if (uri == null || uri.startsWith(ApiPaths.ACTUATOR)) {
            return false;
        }
        if (uri.startsWith(ApiPaths.AVAILABILITY) || uri.startsWith(ApiPaths.BOOKINGS)) {
            return true;
        }
        return ApiPaths.TECHNICIAN_SCHEDULE_PATH.matcher(uri).matches();
    }

    /**
     * Resolves the rate-limit bucket key for the caller.
     */
    static String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatus status = ErrorCode.RATE_LIMIT_EXCEEDED.status();
        ErrorResponse body = new ErrorResponse(timeService.toIst(Instant.now()), status.value(), status.name(),
                ErrorCode.RATE_LIMIT_EXCEEDED.name(), ErrorCode.RATE_LIMIT_EXCEEDED.message(), request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
