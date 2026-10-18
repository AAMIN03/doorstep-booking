package com.booking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.booking.entity.BookingEntity;
import com.booking.entity.TechnicianEntity;
import com.booking.entity.ZoneEntity;
import com.booking.enums.BookingStatus;
import com.booking.enums.Skill;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchedulingRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-15T02:30:00Z");

    @Mock
    private TechnicianEntity tech;

    @Mock
    private ZoneEntity zone;

    private SchedulingRules rules;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        rules = new SchedulingRules(clock, new TimeService());
        when(tech.getZone()).thenReturn(zone);
        when(zone.getId()).thenReturn(1L);
        when(tech.getSkills()).thenReturn(Set.of(Skill.BATTERY_FIT));
        when(tech.getShiftStart()).thenReturn(LocalTime.of(9, 0));
        when(tech.getShiftEnd()).thenReturn(LocalTime.of(18, 0));
    }

    @Test
    void technicianHasRequiredSkill() {
        assertTrue(rules.hasSkill(tech, Skill.BATTERY_FIT));
    }

    @Test
    void technicianLacksSkill() {
        assertFalse(rules.hasSkill(tech, Skill.JUMPSTART));
    }

    @Test
    void technicianWrongZone() {
        assertFalse(rules.inZone(tech, 99L));
        assertTrue(rules.inZone(tech, 1L));
    }

    @Test
    void overlapDetected() {
        Instant start = Instant.parse("2026-10-20T04:30:00Z");
        Instant end = Instant.parse("2026-10-20T05:30:00Z");
        BookingEntity existing = booking(Instant.parse("2026-10-20T05:00:00Z"), Instant.parse("2026-10-20T06:00:00Z"),
                BookingStatus.CONFIRMED);
        assertTrue(rules.conflicts(start, end, List.of(existing)));
    }

    @Test
    void validBoundaryNoOverlap() {
        Instant start = Instant.parse("2026-10-20T04:30:00Z");
        Instant end = Instant.parse("2026-10-20T05:30:00Z");
        BookingEntity existing = booking(Instant.parse("2026-10-20T06:30:00Z"), Instant.parse("2026-10-20T07:30:00Z"),
                BookingStatus.CONFIRMED);
        assertFalse(rules.conflicts(start, end, List.of(existing)));
    }

    @Test
    void travelBufferViolation() {
        Instant start = Instant.parse("2026-10-20T05:30:00Z");
        Instant end = Instant.parse("2026-10-20T06:30:00Z");
        BookingEntity existing = booking(Instant.parse("2026-10-20T04:30:00Z"), Instant.parse("2026-10-20T05:30:00Z"),
                BookingStatus.CONFIRMED);
        assertTrue(rules.conflicts(start, end, List.of(existing)));
    }

    @Test
    void sufficientTravelBuffer() {
        Instant start = Instant.parse("2026-10-20T06:30:00Z");
        Instant end = Instant.parse("2026-10-20T07:30:00Z");
        BookingEntity existing = booking(Instant.parse("2026-10-20T04:30:00Z"), Instant.parse("2026-10-20T05:30:00Z"),
                BookingStatus.CONFIRMED);
        assertFalse(rules.conflicts(start, end, List.of(existing)));
    }

    @Test
    void bookingOutsideShift() {
        Instant start = Instant.parse("2026-10-20T12:30:00Z");
        Instant end = Instant.parse("2026-10-20T13:30:00Z");
        assertFalse(rules.fitsShift(tech, start, end));
    }

    @Test
    void bookingEndsExactlyAtShiftEnd() {
        Instant start = Instant.parse("2026-10-20T11:30:00Z");
        Instant end = Instant.parse("2026-10-20T12:30:00Z");
        assertTrue(rules.fitsShift(tech, start, end));
    }

    @Test
    void invalidSlotAlignment() {
        Instant start = Instant.parse("2026-10-20T04:45:00Z");
        assertFalse(rules.isSlotAligned(start));
    }

    @Test
    void bookingLessThanTwoHoursFromNow() {
        Instant start = Instant.parse("2026-10-15T03:00:00Z");
        assertFalse(rules.withinHorizon(start));
    }

    @Test
    void bookingMoreThanSevenDaysFromNow() {
        Instant start = Instant.parse("2026-10-23T04:30:00Z");
        assertFalse(rules.withinHorizon(start));
    }

    @Test
    void horizonLowerBoundInclusive() {
        Instant start = Instant.parse("2026-10-15T04:30:00Z");
        assertTrue(rules.withinHorizon(start));
    }

    @Test
    void horizonUpperBoundInclusive() {
        Instant start = Instant.parse("2026-10-22T02:30:00Z");
        assertTrue(rules.withinHorizon(start));
    }

    @Test
    void cancelledBookingDoesNotBlock() {
        Instant start = Instant.parse("2026-10-20T04:30:00Z");
        Instant end = Instant.parse("2026-10-20T05:30:00Z");
        BookingEntity existing = booking(Instant.parse("2026-10-20T04:30:00Z"), Instant.parse("2026-10-20T05:30:00Z"),
                BookingStatus.CANCELLED);
        assertFalse(rules.conflicts(start, end, List.of(existing)));
    }

    @Test
    void cancellationExactlyOneHourBeforeStartAllowed() {
        Instant start = Instant.parse("2026-10-15T03:30:00Z");
        assertFalse(rules.cancelTooLate(start));
    }

    @Test
    void cancellationTooLate() {
        Instant start = Instant.parse("2026-10-15T03:00:00Z");
        assertTrue(rules.cancelTooLate(start));
    }

    private static BookingEntity booking(Instant start, Instant end, BookingStatus status) {
        BookingEntity b = new BookingEntity();
        b.setStartTime(start);
        b.setEndTime(end);
        b.setStatus(status);
        return b;
    }
}
