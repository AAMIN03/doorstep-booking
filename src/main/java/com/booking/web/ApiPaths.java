package com.booking.web;

import java.util.regex.Pattern;

/** Path templates shared by the controller and the servlet filters. */
public final class ApiPaths {

    public static final String AVAILABILITY = "/availability";
    public static final String BOOKINGS = "/bookings";
    public static final String BOOKING = "/bookings/{id}";
    public static final String CANCEL = "/bookings/{id}/cancel";
    public static final String TECHNICIAN_SCHEDULE = "/technicians/{id}/schedule";
    public static final String ACTUATOR = "/actuator";

    public static final Pattern BOOKING_ID = Pattern.compile("/bookings/\\d+");
    public static final Pattern CANCEL_BOOKING = Pattern.compile("/bookings/\\d+/cancel");
    public static final Pattern TECHNICIAN_SCHEDULE_PATH = Pattern.compile("/technicians/\\d+/schedule");

    private ApiPaths() {
    }

    public static String bookingLocation(Long id) {
        return BOOKINGS + "/" + id;
    }
}
