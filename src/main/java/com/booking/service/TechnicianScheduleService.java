package com.booking.service;

import com.booking.dto.PageResponse;
import com.booking.dto.ScheduleResponse;
import com.booking.enums.BookingStatus;
import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.repository.TechnicianRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechnicianScheduleService {

    private static final Logger log = LoggerFactory.getLogger(TechnicianScheduleService.class);

    private final TechnicianRepository technicianRepository;
    private final BookingQueryService bookingQueryService;

    public TechnicianScheduleService(TechnicianRepository technicianRepository,
            BookingQueryService bookingQueryService) {
        this.technicianRepository = technicianRepository;
        this.bookingQueryService = bookingQueryService;
    }

    /**
     * Paginated bookings for a technician. See {@link BookingQueryService#technicianSchedule} for filter semantics.
     *
     * @param startDate
     *            optional IST range start (defaults to {@code date})
     * @param endDate
     *            optional IST range end (defaults to {@code date})
     */
    @Transactional(readOnly = true)
    public ScheduleResponse schedule(Long technicianId, LocalDate date, LocalDate startDate, LocalDate endDate,
            BookingStatus status, Pageable pageable) {
        if (!technicianRepository.existsById(technicianId)) {
            throw new ApiException(ErrorCode.UNKNOWN_TECHNICIAN);
        }
        PageResponse<com.booking.dto.BookingResponse> page = bookingQueryService.technicianSchedule(technicianId, date,
                startDate, endDate, status, pageable);
        log.debug("Technician schedule id={} date={} status={} total={}", technicianId, date, status,
                page.totalElements());
        return new ScheduleResponse(technicianId, date, page);
    }
}
