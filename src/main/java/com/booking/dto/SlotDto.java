package com.booking.dto;

import java.time.ZonedDateTime;

public record SlotDto(ZonedDateTime startTime, ZonedDateTime endTime) {
}
