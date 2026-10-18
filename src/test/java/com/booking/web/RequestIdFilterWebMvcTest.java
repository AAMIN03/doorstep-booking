package com.booking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies {@link RequestIdFilter} on the servlet stack without Spring Boot context or Docker.
 */
class RequestIdFilterWebMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PingController()).addFilters(new RequestIdFilter()).build();
    }

    @Test
    void responseIncludesGeneratedRequestIdWhenHeaderAbsent() throws Exception {
        MvcResult result = mockMvc.perform(get("/ping")).andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER)).andReturn();
        String requestId = result.getResponse().getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void echoesClientRequestIdWhenHeaderPresent() throws Exception {
        String clientId = "client-correlation-42";
        mockMvc.perform(get("/ping").header(RequestIdFilter.REQUEST_ID_HEADER, clientId)).andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, clientId));
    }

    @RestController
    static class PingController {
        @GetMapping("/ping")
        String ping() {
            return "ok";
        }
    }
}
