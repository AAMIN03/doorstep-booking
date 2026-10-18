package com.booking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestClockConfig.class})
class DoorstepBookingApplicationTests {

    @Test
    void contextLoads() {}
}
