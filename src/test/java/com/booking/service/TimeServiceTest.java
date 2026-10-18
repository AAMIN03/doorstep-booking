package com.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class TimeServiceTest {

    private final TimeService timeService = new TimeService();

    @Test
    void acceptsAsiaKolkataZone() {
        ZonedDateTime ist = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, TimeService.ZONE);
        Instant utc = timeService.toUtcInstant(ist);
        assertThat(utc).isEqualTo(Instant.parse("2026-10-20T04:30:00Z"));
    }

    @Test
    void acceptsFixedIstOffset() {
        ZonedDateTime ist = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));
        assertThat(timeService.toUtcInstant(ist)).isEqualTo(Instant.parse("2026-10-20T04:30:00Z"));
    }

    @Test
    void rejectsNonIstZone() {
        ZonedDateTime utc = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, ZoneOffset.UTC);
        assertThatThrownBy(() -> timeService.toUtcInstant(utc))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(api.getMessage()).contains("must use IST");
                });
    }
}
