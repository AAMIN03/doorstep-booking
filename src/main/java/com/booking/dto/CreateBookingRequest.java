package com.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZonedDateTime;

public record CreateBookingRequest(
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid Indian mobile number") String customerPhone,
        @NotNull Long serviceId, @NotBlank @Pattern(regexp = "^\\d{6}$") String pincode,
        @NotNull ZonedDateTime startTime) {
}
