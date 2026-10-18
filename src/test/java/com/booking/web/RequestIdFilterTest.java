package com.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for {@link RequestIdFilter} MDC lifecycle (no Spring context). */
@ExtendWith(MockitoExtension.class)
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsRequestIdInMdcDuringChainAndClearsAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)).isNull();
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void usesIncomingHeaderForMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "  abc-123  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (req, res) -> assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)).isEqualTo("abc-123"));

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("abc-123");
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID_KEY)).isNull();
    }
}
