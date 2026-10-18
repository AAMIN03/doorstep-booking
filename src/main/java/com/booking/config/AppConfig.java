package com.booking.config;

import com.booking.service.TimeService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** Wall-clock and horizon rules use IST; {@link Clock#instant()} remains UTC-compatible. */
    @Bean
    Clock clock() {
        return Clock.system(TimeService.ZONE);
    }
}
