package com.booking.service;

import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Single business timezone: {@link #ZONE} (IST). API datetimes must use IST; persistence uses UTC instants.
 */
@Component
public class TimeService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private static final ZoneOffset IST_OFFSET = ZoneOffset.ofHoursMinutes(5, 30);

    /**
     * Converts an API {@code ZonedDateTime} to UTC for storage after verifying IST zone/offset.
     *
     * @throws ApiException 400 {@link ErrorCode#VALIDATION_ERROR} if not IST
     */
    public Instant toUtcInstant(ZonedDateTime zoned) {
        requireIst(zoned, "startTime");
        return zoned.withZoneSameInstant(ZoneId.of("UTC")).toInstant();
    }

    /**
     * Ensures {@code zoned} uses {@link #ZONE} or fixed {@code +05:30} (no other offsets or regions).
     */
    public void requireIst(ZonedDateTime zoned, String fieldName) {
        if (zoned == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Field '" + fieldName + "': is required");
        }
        ZoneId zone = zoned.getZone();
        if (zone.equals(ZONE)) {
            return;
        }
        if (zone instanceof ZoneOffset offset && offset.equals(IST_OFFSET)) {
            return;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Field '" + fieldName + "': must use IST (Asia/Kolkata or +05:30)");
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
