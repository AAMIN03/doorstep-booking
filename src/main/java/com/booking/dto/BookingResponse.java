package com.booking.dto;

import com.booking.enums.BookingStatus;
import java.time.ZonedDateTime;

public record BookingResponse(
        Long id,
        String customerPhone,
        Long serviceId,
        String serviceName,
        String pincode,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        Long technicianId,
        String technicianName,
        BookingStatus status) {
}
