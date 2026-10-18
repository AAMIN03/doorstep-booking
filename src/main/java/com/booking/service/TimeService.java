package com.booking.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class TimeService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    /** Persisted instants are UTC; API uses IST offsets. */
    public Instant toUtcInstant(ZonedDateTime zoned) {
        return zoned.withZoneSameInstant(ZoneId.of("UTC")).toInstant();
    }

    public ZonedDateTime toIst(Instant instant) {
        return instant.atZone(ZONE);
    }

    public LocalDate toIstDate(Instant instant) {
        return toIst(instant).toLocalDate();
    }

    public Instant istToUtc(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).atZone(ZONE).withZoneSameInstant(ZoneId.of("UTC")).toInstant();
    }
}
