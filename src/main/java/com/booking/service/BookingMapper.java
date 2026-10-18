package com.booking.service;

import com.booking.dto.BookingResponse;
import com.booking.entity.BookingEntity;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    private final TimeService timeService;

    public BookingMapper(TimeService timeService) {
        this.timeService = timeService;
    }

    public BookingResponse toResponse(BookingEntity b) {
        return new BookingResponse(
                b.getId(),
                b.getCustomerPhone(),
                b.getService().getId(),
                b.getService().getName(),
                b.getPincode(),
                timeService.toIst(b.getStartTime()),
                timeService.toIst(b.getEndTime()),
                b.getTechnician().getId(),
                b.getTechnician().getName(),
                b.getStatus());
    }
}
