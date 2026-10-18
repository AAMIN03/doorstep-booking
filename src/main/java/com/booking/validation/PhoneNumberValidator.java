package com.booking.validation;

import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.web.CustomerIdentity;
import org.springframework.http.HttpStatus;

/** Indian mobile numbers: 10 digits starting with 6–9 (same rule as {@link com.booking.dto.CreateBookingRequest}). */
public final class PhoneNumberValidator {

    public static final String PATTERN = "^[6-9]\\d{9}$";

    private PhoneNumberValidator() {}

    public static void requireValid(String phone) {
        if (phone == null || !phone.matches(PATTERN)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Parameter 'phone': must be a valid Indian mobile number");
        }
    }

    /** Validates {@link CustomerIdentity#HEADER} format. */
    public static void requireValidCustomerHeader(String phone) {
        if (phone == null || !phone.matches(PATTERN)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Header '" + CustomerIdentity.HEADER + "': must be a valid Indian mobile number");
        }
    }

    /**
     * Ensures list-by-phone requests cannot read another customer's history by query string alone.
     */
    public static void requireHeaderMatchesQueryPhone(String headerPhone, String queryPhone) {
        requireValidCustomerHeader(headerPhone);
        requireValid(queryPhone);
        if (!headerPhone.equals(queryPhone)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Parameter 'phone': must match Header '" + CustomerIdentity.HEADER + "'");
        }
    }
}
