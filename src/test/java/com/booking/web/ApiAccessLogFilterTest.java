package com.booking.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiAccessLogFilterTest {

    private final ApiAccessLogFilter filter = new ApiAccessLogFilter();
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiAccessLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiAccessLogFilter.class);
        logger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void logsAvailabilityRequestAndResponseWithRequestId() throws Exception {
        MDC.put(RequestIdFilter.MDC_REQUEST_ID_KEY, "req-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/availability");
        request.setQueryString("serviceId=1&pincode=400001&date=2026-10-20");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(200);
            http.getWriter().write("{\"availableSlots\":[]}");
        });

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "req-1");
        assertThat(fields(event)).contains("httpMethod=GET", "path=/availability",
                "query=serviceId=1&pincode=400001&date=2026-10-20", "status=200",
                "responseBody={\"availableSlots\":[]}");
        assertThat(response.getContentAsString()).isEqualTo("{\"availableSlots\":[]}");
    }

    @Test
    void logsCreateRequestBodyAndCancelPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/bookings");
        request.setContent("{\"serviceId\":1}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            ((HttpServletRequest) req).getInputStream().readAllBytes();
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.getWriter().write("{\"id\":1}");
        };

        filter.doFilter(request, response, chain);

        assertThat(fields(appender.list.get(0))).contains("requestBody={\"serviceId\":1}", "status=201");

        appender.list.clear();
        MockHttpServletRequest cancel = new MockHttpServletRequest("POST", "/bookings/4/cancel");
        filter.doFilter(cancel, new MockHttpServletResponse(),
                (req, res) -> ((HttpServletResponse) res).setStatus(200));
        assertThat(fields(appender.list.get(0))).contains("path=/bookings/4/cancel");
    }

    @Test
    void skipsActuatorAndSchedule() throws Exception {
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(200);
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(), chain);
        filter.doFilter(new MockHttpServletRequest("GET", "/technicians/1/schedule"), new MockHttpServletResponse(),
                chain);
        assertThat(appender.list).isEmpty();
    }

    private static String fields(ILoggingEvent event) {
        return Arrays.toString(event.getArgumentArray());
    }
}
