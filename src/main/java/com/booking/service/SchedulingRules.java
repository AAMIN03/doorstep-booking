package com.booking.service;

import com.booking.entity.BookingEntity;
import com.booking.entity.TechnicianEntity;
import com.booking.enums.BookingStatus;
import com.booking.enums.Skill;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SchedulingRules {

    public static final Duration SLOT_LENGTH = Duration.ofMinutes(30);
    public static final int SLOTS_PER_DAY = (int) Duration.ofDays(1).dividedBy(SLOT_LENGTH);
    public static final Duration TRAVEL_BUFFER = SLOT_LENGTH;
    public static final Duration MIN_HORIZON = Duration.ofHours(2);
    public static final Duration MAX_HORIZON = Duration.ofDays(7);
    public static final Duration CANCEL_WINDOW = Duration.ofHours(1);

    private final Clock clock;
    private final TimeService timeService;

    public SchedulingRules(Clock clock, TimeService timeService) {
        this.clock = clock;
        this.timeService = timeService;
    }

    public boolean isSlotAligned(Instant start) {
        ZonedDateTime ist = timeService.toIst(start);
        return ist.getMinute() % SLOT_LENGTH.toMinutes() == 0 && ist.getSecond() == 0 && ist.getNano() == 0;
    }

    /** Inclusive lower bound (now+2h) and inclusive upper bound (now+7d). */
    public boolean withinHorizon(Instant start) {
        Instant now = clock.instant();
        return !start.isBefore(now.plus(MIN_HORIZON)) && !start.isAfter(now.plus(MAX_HORIZON));
    }

    /**
     * The job must sit inside the shift on the start instant's IST date. An end after midnight is outside the shift,
     * because comparing {@code LocalTime} alone would treat 00:00 as earlier than the shift end.
     */
    public boolean fitsShift(TechnicianEntity tech, Instant start, Instant end) {
        ZonedDateTime startIst = timeService.toIst(start);
        ZonedDateTime endIst = timeService.toIst(end);
        LocalDate shiftDate = startIst.toLocalDate();
        ZonedDateTime shiftStart = shiftDate.atTime(tech.getShiftStart()).atZone(TimeService.ZONE);
        ZonedDateTime shiftEnd = shiftDate.atTime(tech.getShiftEnd()).atZone(TimeService.ZONE);
        return !startIst.isBefore(shiftStart) && !endIst.isAfter(shiftEnd);
    }

    public boolean hasSkill(TechnicianEntity tech, Skill skill) {
        return tech.getSkills().contains(skill);
    }

    public boolean inZone(TechnicianEntity tech, Long zoneId) {
        return tech.getZone().getId().equals(zoneId);
    }

    /** Active bookings only; completed history does not block future slots. */
    public boolean conflicts(Instant newStart, Instant newEnd, List<BookingEntity> existing) {
        for (BookingEntity b : existing) {
            if (b.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }
            if (b.getStatus() != BookingStatus.CONFIRMED) {
                continue;
            }
            if (overlapsWithBuffer(newStart, newEnd, b.getStartTime(), b.getEndTime())) {
                return true;
            }
        }
        return false;
    }

    /** Interval convention [start,end) with 30m post-job buffer between jobs. */
    public boolean overlapsWithBuffer(Instant ns, Instant ne, Instant es, Instant ee) {
        return ns.isBefore(ee.plus(TRAVEL_BUFFER)) && ne.plus(TRAVEL_BUFFER).isAfter(es);
    }

    public boolean cancelTooLate(Instant start) {
        return clock.instant().plus(CANCEL_WINDOW).isAfter(start);
    }
}
