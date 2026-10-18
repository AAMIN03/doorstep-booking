package com.booking.web;

/** Header that makes {@code POST /bookings} safe to retry. */
public final class Idempotency {

    public static final String HEADER = "Idempotency-Key";

    private Idempotency() {
    }
}
