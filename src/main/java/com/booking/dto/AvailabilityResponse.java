package com.booking.dto;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(Long serviceId, String pincode, LocalDate date, List<SlotDto> availableSlots) {
}
