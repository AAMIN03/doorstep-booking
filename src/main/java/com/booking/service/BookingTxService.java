package com.booking.service;

import com.booking.dto.CreateBookingRequest;
import com.booking.entity.BookingEntity;
import com.booking.entity.IdempotencyRecordEntity;
import com.booking.entity.ServiceEntity;
import com.booking.entity.TechnicianEntity;
import com.booking.entity.ZonePincodeEntity;
import com.booking.enums.BookingStatus;
import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.repository.BookingRepository;
import com.booking.repository.IdempotencyRecordRepository;
import com.booking.repository.ServiceRepository;
import com.booking.repository.TechnicianRepository;
import com.booking.repository.ZonePincodeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingTxService {

    private final ServiceRepository serviceRepository;
    private final ZonePincodeRepository zonePincodeRepository;
    private final TechnicianRepository technicianRepository;
    private final BookingRepository bookingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final SchedulingRules rules;
    private final TimeService timeService;
    private final BookingMapper mapper;
    private final Clock clock;

    public BookingTxService(ServiceRepository serviceRepository, ZonePincodeRepository zonePincodeRepository,
            TechnicianRepository technicianRepository, BookingRepository bookingRepository,
            IdempotencyRecordRepository idempotencyRecordRepository, SchedulingRules rules, TimeService timeService,
            BookingMapper mapper, Clock clock) {
        this.serviceRepository = serviceRepository;
        this.zonePincodeRepository = zonePincodeRepository;
        this.technicianRepository = technicianRepository;
        this.bookingRepository = bookingRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.rules = rules;
        this.timeService = timeService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public BookingService.BookingResult create(String idempotencyKey, CreateBookingRequest request, String hash) {
        IdempotencyRecordEntity record = IdempotencyRecordEntity.of(idempotencyKey, hash, clock.instant());
        idempotencyRecordRepository.saveAndFlush(record);

        ServiceEntity service = serviceRepository.findById(request.serviceId()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_SERVICE, "Service not found"));
        ZonePincodeEntity zp = zonePincodeRepository.findByPincodeWithZone(request.pincode()).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_PINCODE, "Pincode not served"));

        Instant start = timeService.toUtcInstant(request.startTime());
        Instant end = start.plusSeconds(service.getDurationMinutes() * 60L);
        validateSlot(start);

        List<TechnicianEntity> eligible = technicianRepository.findEligible(zp.getZone().getId(),
                service.getRequiredSkill());
        if (eligible.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NO_TECHNICIAN_AVAILABLE,
                    "No technician is available for the requested slot.");
        }

        List<Long> ids = eligible.stream().map(TechnicianEntity::getId).sorted().toList();
        technicianRepository.lockByIdsInOrder(ids);
        LocalDate day = timeService.toIstDate(start);
        Instant dayStart = timeService.istToUtc(day, LocalTime.MIN);
        Instant dayEnd = timeService.istToUtc(day.plusDays(1), LocalTime.MIN);
        List<BookingEntity> active = bookingRepository.findActiveInRangeForUpdate(ids,
                dayStart.minus(SchedulingRules.TRAVEL_BUFFER), dayEnd.plus(SchedulingRules.TRAVEL_BUFFER));

        TechnicianEntity chosen = chooseTechnician(eligible, active, start, end, dayStart, dayEnd);
        if (chosen == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NO_TECHNICIAN_AVAILABLE,
                    "No technician is available for the requested slot.");
        }

        List<BookingEntity> freshForTech = bookingRepository.findActiveInRangeForUpdate(List.of(chosen.getId()),
                start.minus(SchedulingRules.TRAVEL_BUFFER), end.plus(SchedulingRules.TRAVEL_BUFFER));
        if (rules.conflicts(start, end, freshForTech)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NO_TECHNICIAN_AVAILABLE,
                    "No technician is available for the requested slot.");
        }

        BookingEntity booking = new BookingEntity();
        booking.setCustomerPhone(request.customerPhone());
        booking.setService(service);
        booking.setPincode(request.pincode());
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setTechnician(chosen);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        record.setBooking(booking);
        return new BookingService.BookingResult(mapper.toResponse(booking), true);
    }

    private void validateSlot(Instant start) {
        if (!rules.isSlotAligned(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_SLOT,
                    "Start time must align to 30-minute boundaries");
        }
        if (!rules.withinHorizon(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OUTSIDE_HORIZON,
                    "Booking must be between 2 hours and 7 days from now");
        }
    }

    private TechnicianEntity chooseTechnician(List<TechnicianEntity> eligible, List<BookingEntity> active,
            Instant start, Instant end, Instant dayStart, Instant dayEnd) {
        List<TechnicianEntity> candidates = new ArrayList<>();
        for (TechnicianEntity tech : eligible) {
            if (!rules.fitsShift(tech, start, end)) {
                continue;
            }
            List<BookingEntity> techBookings = active.stream()
                    .filter(b -> b.getTechnician().getId().equals(tech.getId())).toList();
            if (!rules.conflicts(start, end, techBookings)) {
                candidates.add(tech);
            }
        }
        return candidates.stream()
                .min(Comparator.comparingLong((TechnicianEntity t) -> bookingRepository.countActiveForDay(t.getId(),
                        dayStart, dayEnd, BookingStatus.CANCELLED)).thenComparing(TechnicianEntity::getId))
                .orElse(null);
    }
}
