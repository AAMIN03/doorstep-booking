package com.booking.service;

import com.booking.dto.AvailabilityResponse;
import com.booking.dto.SlotDto;
import com.booking.entity.ServiceEntity;
import com.booking.entity.TechnicianEntity;
import com.booking.entity.ZonePincodeEntity;
import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.repository.BookingRepository;
import com.booking.repository.ServiceRepository;
import com.booking.repository.TechnicianRepository;
import com.booking.repository.ZonePincodeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final ServiceRepository serviceRepository;
    private final ZonePincodeRepository zonePincodeRepository;
    private final TechnicianRepository technicianRepository;
    private final BookingRepository bookingRepository;
    private final SchedulingRules rules;
    private final TimeService timeService;

    public AvailabilityService(ServiceRepository serviceRepository, ZonePincodeRepository zonePincodeRepository,
            TechnicianRepository technicianRepository, BookingRepository bookingRepository, SchedulingRules rules,
            TimeService timeService) {
        this.serviceRepository = serviceRepository;
        this.zonePincodeRepository = zonePincodeRepository;
        this.technicianRepository = technicianRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.timeService = timeService;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(Long serviceId, String pincode, LocalDate date) {
        ServiceEntity service = serviceRepository.findById(serviceId).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_SERVICE, "Service not found"));
        ZonePincodeEntity zp = zonePincodeRepository.findByPincodeWithZone(pincode).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_PINCODE, "Pincode not served"));
        List<TechnicianEntity> eligible = technicianRepository.findEligible(zp.getZone().getId(),
                service.getRequiredSkill());
        List<SlotDto> slots = new ArrayList<>();
        int duration = service.getDurationMinutes();
        Instant dayStart = timeService.istToUtc(date, LocalTime.MIN);
        Instant dayEnd = timeService.istToUtc(date.plusDays(1), LocalTime.MIN);
        List<Long> techIds = eligible.stream().map(TechnicianEntity::getId).toList();
        List<com.booking.entity.BookingEntity> dayBookings = techIds.isEmpty() ? List.of()
                : bookingRepository.findActiveInRange(techIds, dayStart.minus(SchedulingRules.TRAVEL_BUFFER),
                        dayEnd.plus(SchedulingRules.TRAVEL_BUFFER));

        LocalTime slot = LocalTime.MIDNIGHT;
        for (int i = 0; i < 48; i++) {
            Instant start = timeService.istToUtc(date, slot);
            Instant end = start.plusSeconds(duration * 60L);
            if (rules.isSlotAligned(start) && rules.withinHorizon(start)
                    && anyTechnicianAvailable(eligible, dayBookings, start, end)) {
                slots.add(new SlotDto(timeService.toIst(start), timeService.toIst(end)));
            }
            slot = slot.plusMinutes(30);
        }
        log.debug("Availability serviceId={} pincode={} date={} eligibleTechnicians={} slots={}", serviceId, pincode,
                date, eligible.size(), slots.size());
        return new AvailabilityResponse(serviceId, pincode, date, slots);
    }

    private boolean anyTechnicianAvailable(List<TechnicianEntity> eligible,
            List<com.booking.entity.BookingEntity> bookings, Instant start, Instant end) {
        for (TechnicianEntity tech : eligible) {
            if (!rules.fitsShift(tech, start, end)) {
                continue;
            }
            List<com.booking.entity.BookingEntity> techBookings = bookings.stream()
                    .filter(b -> b.getTechnician().getId().equals(tech.getId())).toList();
            if (!rules.conflicts(start, end, techBookings)) {
                return true;
            }
        }
        return false;
    }
}
