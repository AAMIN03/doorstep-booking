package com.booking.actuator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises {@code GET /actuator/health} without MySQL or Testcontainers.
 * <p>
 * Uses a minimal {@link TestApplication} (auto-configuration only, no {@code com.booking} component scan) with
 * JDBC/JPA/Flyway auto-config excluded and {@code management.health.db.enabled=false}, so the health endpoint reports
 * {@code UP} from the servlet stack alone. {@link RequestIdFilter} is imported explicitly to mirror production filter
 * behavior on actuator routes.
 */
@SpringBootTest(classes = ActuatorHealthMockMvcTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "management.endpoints.web.exposure.include=health", "management.health.db.enabled=false",
        "spring.autoconfigure.exclude=" + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration" })
@AutoConfigureMockMvc
@Import(RequestIdFilter.class)
class ActuatorHealthMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUpWithRequestId() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @EnableAutoConfiguration
    static class TestApplication {
    }
}
