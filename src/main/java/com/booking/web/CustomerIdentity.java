package com.booking.web;

/**
 * Customer identification without login: callers prove identity with the {@value #HEADER} HTTP header.
 * <p>
 * Must match the booking's {@code customerPhone} for single-booking access and must match the {@code phone} query
 * parameter on {@code GET /bookings}. Create booking still uses {@code customerPhone} in the JSON body only.
 */
public final class CustomerIdentity {

    /** Required on customer-scoped booking reads and cancel; value is a 10-digit Indian mobile. */
    public static final String HEADER = "X-Customer-Phone";

    private CustomerIdentity() {
    }
}
