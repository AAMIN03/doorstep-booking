package com.booking.service;

import com.booking.dto.PageResponse;
import com.booking.dto.ScheduleResponse;
import com.booking.enums.BookingStatus;
import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.repository.TechnicianRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechnicianScheduleService {

    private final TechnicianRepository technicianRepository;
    private final BookingQueryService bookingQueryService;

    public TechnicianScheduleService(
            TechnicianRepository technicianRepository, BookingQueryService bookingQueryService) {
        this.technicianRepository = technicianRepository;
        this.bookingQueryService = bookingQueryService;
    }

    /**
     * Paginated bookings for a technician. See {@link BookingQueryService#technicianSchedule} for filter semantics.
     *
     * @param startDate optional IST range start (defaults to {@code date})
     * @param endDate optional IST range end (defaults to {@code date})
     */
    @Transactional(readOnly = true)
    public ScheduleResponse schedule(
            Long technicianId,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate,
            BookingStatus status,
            Pageable pageable) {
        if (!technicianRepository.existsById(technicianId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_TECHNICIAN, "Technician not found");
        }
        PageResponse<com.booking.dto.BookingResponse> page = bookingQueryService.technicianSchedule(
                technicianId, date, startDate, endDate, status, pageable);
        return new ScheduleResponse(technicianId, date, page);
    }
}
