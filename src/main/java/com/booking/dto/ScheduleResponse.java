package com.booking.dto;

import java.time.LocalDate;

/** Technician day schedule with paginated bookings. */
public record ScheduleResponse(Long technicianId, LocalDate date, PageResponse<BookingResponse> bookings) {
}
