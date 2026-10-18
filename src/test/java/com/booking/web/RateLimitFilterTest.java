package com.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.booking.exception.ErrorCode;
import com.booking.ratelimit.RateLimitOperations;
import com.booking.service.TimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for {@link RateLimitFilter} path matching and 429 responses. */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitOperations rateLimitOperations;

    private RateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TimeService timeService = new TimeService();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(rateLimitOperations, timeService, objectMapper);
    }

    @Test
    void appliesToBookingApiPathsOnly() {
        assertThat(RateLimitFilter.appliesTo("/availability")).isTrue();
        assertThat(RateLimitFilter.appliesTo("/bookings")).isTrue();
        assertThat(RateLimitFilter.appliesTo("/bookings/1")).isTrue();
        assertThat(RateLimitFilter.appliesTo("/technicians/1/schedule")).isTrue();
        assertThat(RateLimitFilter.appliesTo("/actuator/health")).isFalse();
        assertThat(RateLimitFilter.appliesTo("/other")).isFalse();
    }

    @Test
    void resolveClientKeyUsesForwardedForFirstHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        assertThat(RateLimitFilter.resolveClientKey(request)).isEqualTo("203.0.113.1");
    }

    @Test
    void returns429WhenLimitExceeded() throws Exception {
        when(rateLimitOperations.tryConsume(anyString())).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/availability");
        request.setRemoteAddr("192.0.2.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(chain.getRequest()).isNull();
        var error = objectMapper.readValue(response.getContentAsByteArray(), com.booking.dto.ErrorResponse.class);
        assertThat(error.code()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.name());
        assertThat(error.message()).isEqualTo("Rate limit exceeded. Try again later.");
        assertThat(error.path()).isEqualTo("/availability");
    }

    @Test
    void allowsRequestWhenTokenAvailable() throws Exception {
        when(rateLimitOperations.tryConsume(anyString())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/bookings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(rateLimitOperations).tryConsume("127.0.0.1");
    }
}
