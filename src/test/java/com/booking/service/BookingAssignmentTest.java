package com.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.booking.entity.BookingEntity;
import com.booking.entity.TechnicianEntity;
import com.booking.enums.BookingStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingAssignmentTest {

    private static final Instant DAY_START = Instant.parse("2026-10-19T18:30:00Z");
    private static final Instant DAY_END = Instant.parse("2026-10-20T18:30:00Z");
    private static final Instant SLOT_START = Instant.parse("2026-10-20T04:30:00Z");
    private static final Instant SLOT_END = Instant.parse("2026-10-20T05:30:00Z");

    private SchedulingRules rules;
    private TechnicianEntity ravi;
    private TechnicianEntity amit;

    @BeforeEach
    void setUp() {
        rules = new SchedulingRules(Clock.fixed(Instant.parse("2026-10-15T02:30:00Z"), ZoneOffset.UTC),
                new TimeService());
        ravi = tech(1L);
        amit = tech(3L);
    }

    @Test
    void tieBreaksToLowestTechnicianId() {
        TechnicianEntity chosen = BookingTxService.chooseTechnician(rules, List.of(amit, ravi), List.of(), SLOT_START,
                SLOT_END, DAY_START, DAY_END);
        assertThat(chosen.getId()).isEqualTo(1L);
    }

    @Test
    void picksTechnicianWithFewerBookingsThatDay() {
        BookingEntity raviAfternoon = booking(ravi, BookingStatus.CONFIRMED, Instant.parse("2026-10-20T08:30:00Z"),
                Instant.parse("2026-10-20T09:30:00Z"));
        TechnicianEntity chosen = BookingTxService.chooseTechnician(rules, List.of(ravi, amit), List.of(raviAfternoon),
                SLOT_START, SLOT_END, DAY_START, DAY_END);
        assertThat(chosen.getId()).isEqualTo(3L);
    }

    @Test
    void completedBookingsCountAndCancelledDoNot() {
        BookingEntity completed = booking(ravi, BookingStatus.COMPLETED, Instant.parse("2026-10-20T08:30:00Z"),
                Instant.parse("2026-10-20T09:30:00Z"));
        BookingEntity cancelled = booking(amit, BookingStatus.CANCELLED, Instant.parse("2026-10-20T08:30:00Z"),
                Instant.parse("2026-10-20T09:30:00Z"));
        assertThat(BookingTxService.bookingsThatDay(List.of(completed, cancelled), 1L, DAY_START, DAY_END))
                .isEqualTo(1);
        assertThat(BookingTxService.bookingsThatDay(List.of(completed, cancelled), 3L, DAY_START, DAY_END)).isZero();
        TechnicianEntity chosen = BookingTxService.chooseTechnician(rules, List.of(ravi, amit),
                List.of(completed, cancelled), SLOT_START, SLOT_END, DAY_START, DAY_END);
        assertThat(chosen.getId()).isEqualTo(3L);
    }

    @Test
    void previousDayJobDoesNotCountButStillBlocks() {
        BookingEntity overnight = booking(ravi, BookingStatus.CONFIRMED, DAY_START.minus(Duration.ofHours(1)),
                SLOT_START.plus(Duration.ofMinutes(30)));
        assertThat(BookingTxService.bookingsThatDay(List.of(overnight), 1L, DAY_START, DAY_END)).isZero();
        TechnicianEntity chosen = BookingTxService.chooseTechnician(rules, List.of(ravi, amit), List.of(overnight),
                SLOT_START, SLOT_END, DAY_START, DAY_END);
        assertThat(chosen.getId()).isEqualTo(3L);
    }

    @Test
    void skipsTechnicianWhoseShiftDoesNotCoverAMidnightEnd() {
        Instant start = Instant.parse("2026-10-20T17:30:00Z");
        Instant end = start.plus(Duration.ofHours(1));
        TechnicianEntity chosen = BookingTxService.chooseTechnician(rules, List.of(ravi), List.of(), start, end,
                DAY_START, DAY_END);
        assertThat(chosen).isNull();
    }

    private static TechnicianEntity tech(long id) {
        TechnicianEntity tech = mock(TechnicianEntity.class);
        when(tech.getId()).thenReturn(id);
        when(tech.getShiftStart()).thenReturn(LocalTime.of(8, 0));
        when(tech.getShiftEnd()).thenReturn(LocalTime.of(20, 0));
        return tech;
    }

    private static BookingEntity booking(TechnicianEntity technician, BookingStatus status, Instant start,
            Instant end) {
        BookingEntity booking = new BookingEntity();
        booking.setTechnician(technician);
        booking.setStatus(status);
        booking.setStartTime(start);
        booking.setEndTime(end);
        return booking;
    }
}
