package com.booking.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.ratelimit.RateLimitOperations;
import com.booking.service.TimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MockMvc slice for {@link RateLimitFilter} with a stub controller (no Spring Boot context, no Redis).
 */
class RateLimitFilterWebMvcTest {

    private MockMvc mockMvc;
    private final RateLimitOperations denyAll = clientKey -> false;
    private final RateLimitOperations allowAll = clientKey -> true;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = buildMvc(allowAll, objectMapper);
    }

    private static MockMvc buildMvc(RateLimitOperations operations, ObjectMapper objectMapper) {
        return MockMvcBuilders.standaloneSetup(new PingController())
                .addFilters(new RateLimitFilter(operations, new TimeService(), objectMapper)).build();
    }

    @Test
    void apiPathReturns429WhenRateLimitExceeded() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        MockMvc limited = buildMvc(denyAll, objectMapper);

        limited.perform(get("/availability").accept(MediaType.APPLICATION_JSON)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED")).andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Try again later."));
    }

    @Test
    void apiPathAllowedWhenUnderLimit() throws Exception {
        mockMvc.perform(get("/availability")).andExpect(status().isOk());
    }

    @RestController
    static class PingController {
        @GetMapping("/availability")
        String availability() {
            return "ok";
        }
    }
}
