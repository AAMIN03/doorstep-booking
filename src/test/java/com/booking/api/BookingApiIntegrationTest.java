package com.booking.api;

import static com.booking.api.support.ApiErrorTestSupport.expectApiError;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.booking.TestClockConfig;
import com.booking.TestcontainersConfiguration;
import com.booking.enums.BookingStatus;
import com.booking.repository.BookingRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full-stack API tests (Testcontainers MySQL, fixed {@link com.booking.TestClockConfig} clock).
 * Error cases assert canonical JSON via {@link com.booking.api.support.ApiErrorTestSupport}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestClockConfig.class})
class BookingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void availabilityReturnsSlots() throws Exception {
        mockMvc.perform(get("/availability")
                        .param("serviceId", "1")
                        .param("pincode", "400001")
                        .param("date", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSlots").isArray());
    }

    @Test
    void createGetCancelFlow() throws Exception {
        String body = """
                {"customerPhone":"9876543210","serviceId":1,"pincode":"400001","startTime":"2026-10-20T10:00:00+05:30"}
                """;
        MvcResult created = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.technicianId").exists())
                .andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id"))
                .longValue();
        mockMvc.perform(get("/bookings/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mockMvc.perform(post("/bookings/" + id + "/cancel")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void idempotentRetryReturnsSameBooking() throws Exception {
        String body = """
                {"customerPhone":"9876543210","serviceId":2,"pincode":"400058","startTime":"2026-10-20T11:00:00+05:30"}
                """;
        MvcResult first = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        long idA = ((Number) com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.id"))
                .longValue();
        long idB = ((Number) com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.id"))
                .longValue();
        assertThat(idA).isEqualTo(idB);
    }

    @Test
    void idempotencyConflictOnDifferentPayload() throws Exception {
        String body1 = """
                {"customerPhone":"9876543210","serviceId":2,"pincode":"400058","startTime":"2026-10-20T12:00:00+05:30"}
                """;
        String body2 = """
                {"customerPhone":"9876543211","serviceId":2,"pincode":"400058","startTime":"2026-10-20T12:00:00+05:30"}
                """;
        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "idem-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isCreated());
        expectApiError(
                mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "idem-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)),
                409,
                "CONFLICT",
                "IDEMPOTENCY_CONFLICT",
                "Idempotency key reused with different payload",
                "/bookings");
    }

    @Test
    void unknownServiceAndPincode() throws Exception {
        expectApiError(
                mockMvc.perform(get("/availability")
                        .param("serviceId", "999")
                        .param("pincode", "400001")
                        .param("date", "2026-10-20")),
                404,
                "NOT_FOUND",
                "UNKNOWN_SERVICE",
                "Service not found",
                "/availability");
        expectApiError(
                mockMvc.perform(get("/availability")
                        .param("serviceId", "1")
                        .param("pincode", "999999")
                        .param("date", "2026-10-20")),
                404,
                "NOT_FOUND",
                "UNKNOWN_PINCODE",
                "Pincode not served",
                "/availability");
    }

    @Test
    void unknownBookingReturnsNotFound() throws Exception {
        expectApiError(
                mockMvc.perform(get("/bookings/999999")),
                404,
                "NOT_FOUND",
                "UNKNOWN_BOOKING",
                "Booking not found",
                "/bookings/999999");
    }

    /** {@code POST /bookings/{id}/cancel} when the id does not exist. */
    @Test
    void cancelUnknownBookingReturnsNotFound() throws Exception {
        expectApiError(
                mockMvc.perform(post("/bookings/999999/cancel")),
                404,
                "NOT_FOUND",
                "UNKNOWN_BOOKING",
                "Booking not found",
                "/bookings/999999/cancel");
    }

    /**
     * {@code LATE_CANCELLATION}: start is moved to within one hour of the fixed test clock
     * (cannot be created that way via API because of the 2h booking horizon).
     */
    @Test
    void lateCancellationReturnsConflict() throws Exception {
        long id = createBooking("late-cancel-create", 1, "400001", "2026-10-20T10:00:00+05:30");
        var booking = bookingRepository.findById(id).orElseThrow();
        booking.setStartTime(Instant.parse("2026-10-15T03:00:00Z"));
        bookingRepository.saveAndFlush(booking);

        expectApiError(
                mockMvc.perform(post("/bookings/" + id + "/cancel")),
                409,
                "CONFLICT",
                "LATE_CANCELLATION",
                "Cancellation must be at least 1 hour before start",
                "/bookings/" + id + "/cancel");
    }

    /** Second cancel on the same booking → {@code INVALID_CANCELLATION}. */
    @Test
    void cancelAlreadyCancelledBookingReturnsConflict() throws Exception {
        long id = createBooking("double-cancel", 1, "400001", "2026-10-20T11:00:00+05:30");
        mockMvc.perform(post("/bookings/" + id + "/cancel")).andExpect(status().isOk());

        expectApiError(
                mockMvc.perform(post("/bookings/" + id + "/cancel")),
                409,
                "CONFLICT",
                "INVALID_CANCELLATION",
                "Booking already cancelled",
                "/bookings/" + id + "/cancel");
    }

    /**
     * {@code COMPLETED} is set via repository (no public complete endpoint); cancel → {@code INVALID_CANCELLATION}.
     */
    @Test
    void cancelCompletedBookingReturnsConflict() throws Exception {
        long id = createBooking("completed-cancel", 1, "400001", "2026-10-20T12:00:00+05:30");
        var booking = bookingRepository.findById(id).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.saveAndFlush(booking);

        expectApiError(
                mockMvc.perform(post("/bookings/" + id + "/cancel")),
                409,
                "CONFLICT",
                "INVALID_CANCELLATION",
                "Completed booking cannot be cancelled",
                "/bookings/" + id + "/cancel");
    }

    @Test
    void unknownTechnicianReturnsNotFound() throws Exception {
        expectApiError(
                mockMvc.perform(get("/technicians/999999/schedule").param("date", "2026-10-20")),
                404,
                "NOT_FOUND",
                "UNKNOWN_TECHNICIAN",
                "Technician not found",
                "/technicians/999999/schedule");
    }

    @Test
    void technicianScheduleOrdered() throws Exception {
        mockMvc.perform(get("/technicians/1/schedule").param("date", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technicianId").value(1))
                .andExpect(jsonPath("$.bookings.content").isArray())
                .andExpect(jsonPath("$.bookings.page").value(0));
    }

    /** {@code GET /bookings?phone=} returns {@link com.booking.dto.PageResponse} with matching bookings. */
    @Test
    void listBookingsByPhoneHappyPath() throws Exception {
        String phone = "9876511111";
        createBookingWithPhone("list-phone-1", phone, 1, "400001", "2026-10-20T10:00:00+05:30");
        mockMvc.perform(get("/bookings").param("phone", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].customerPhone").value(phone))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listBookingsByPhonePagination() throws Exception {
        String phone = "9876522222";
        createBookingWithPhone("list-page-a", phone, 1, "400001", "2026-10-20T10:00:00+05:30");
        createBookingWithPhone("list-page-b", phone, 1, "400001", "2026-10-20T14:00:00+05:30");
        mockMvc.perform(get("/bookings").param("phone", phone).param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/bookings").param("phone", phone).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));
        mockMvc.perform(get("/bookings").param("phone", phone).param("size", "1").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listBookingsByPhoneEmptyResult() throws Exception {
        mockMvc.perform(get("/bookings").param("phone", "9876501234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void listBookingsByPhoneInvalidPhone() throws Exception {
        expectApiError(
                mockMvc.perform(get("/bookings").param("phone", "123")),
                400,
                "BAD_REQUEST",
                "VALIDATION_ERROR",
                "Parameter 'phone': must be a valid Indian mobile number",
                "/bookings");
    }

    @Test
    void listBookingsMissingPhoneParameter() throws Exception {
        expectApiError(
                mockMvc.perform(get("/bookings")),
                400,
                "BAD_REQUEST",
                "INVALID_PARAMETER",
                "Parameter 'phone': is required",
                "/bookings");
    }

    /** {@code status} query param filters on {@link BookingStatus}. */
    @Test
    void listBookingsByPhoneStatusFilter() throws Exception {
        String phone = "9876533333";
        long confirmedId = createBookingWithPhone("list-status-conf", phone, 1, "400001", "2026-10-20T10:00:00+05:30");
        long cancelledId = createBookingWithPhone("list-status-canc", phone, 1, "400001", "2026-10-20T14:00:00+05:30");
        mockMvc.perform(post("/bookings/" + cancelledId + "/cancel")).andExpect(status().isOk());

        mockMvc.perform(get("/bookings").param("phone", phone).param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(confirmedId))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));

        mockMvc.perform(get("/bookings").param("phone", phone).param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(cancelledId))
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    /** Inclusive IST {@code startDate} / {@code endDate} filter on booking {@code startTime}. */
    @Test
    void listBookingsByPhoneDateRangeFilter() throws Exception {
        String phone = "9876544444";
        createBookingWithPhone("list-range-a", phone, 1, "400001", "2026-10-20T10:00:00+05:30");
        createBookingWithPhone("list-range-b", phone, 1, "400001", "2026-10-22T10:00:00+05:30");

        mockMvc.perform(get("/bookings")
                        .param("phone", phone)
                        .param("startDate", "2026-10-20")
                        .param("endDate", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/bookings")
                        .param("phone", phone)
                        .param("startDate", "2026-10-21")
                        .param("endDate", "2026-10-22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listBookingsByPhoneInvalidDateRange() throws Exception {
        expectApiError(
                mockMvc.perform(get("/bookings")
                        .param("phone", "9876543210")
                        .param("startDate", "2026-10-22")
                        .param("endDate", "2026-10-20")),
                400,
                "BAD_REQUEST",
                "VALIDATION_ERROR",
                "Parameter 'endDate': must be on or after startDate",
                "/bookings");
    }

    /** {@code sort=startTime,asc|desc} orders paginated results by booking start. */
    @Test
    void listBookingsByPhoneSort() throws Exception {
        String phone = "9876555555";
        createBookingWithPhone("list-sort-early", phone, 1, "400001", "2026-10-20T10:00:00+05:30");
        createBookingWithPhone("list-sort-late", phone, 1, "400001", "2026-10-20T14:00:00+05:30");

        mockMvc.perform(get("/bookings").param("phone", phone).param("sort", "startTime,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].startTime").value("2026-10-20T10:00:00+05:30"));

        mockMvc.perform(get("/bookings").param("phone", phone).param("sort", "startTime,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].startTime").value("2026-10-20T14:00:00+05:30"));
    }

    @Test
    void technicianScheduleStatusFilter() throws Exception {
        long id = createBooking("sched-filter", 1, "400001", "2026-10-20T10:00:00+05:30");
        mockMvc.perform(post("/bookings/" + id + "/cancel")).andExpect(status().isOk());
        mockMvc.perform(get("/technicians/1/schedule")
                        .param("date", "2026-10-20")
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings.content[0].status").value("CANCELLED"));
    }

    /** {@code startDate} / {@code endDate} widen the IST window beyond the required {@code date} anchor. */
    @Test
    void technicianScheduleDateRangeFilter() throws Exception {
        int baseline = scheduleTotalElements("1", "2026-10-21", "2026-10-21", "2026-10-22");
        long dayOne = createBooking("sched-range-day1", 1, "400001", "2026-10-21T08:00:00+05:30");
        long dayTwo = createBooking("sched-range-day2", 1, "400001", "2026-10-22T08:00:00+05:30");

        MvcResult range = mockMvc.perform(get("/technicians/1/schedule")
                        .param("date", "2026-10-21")
                        .param("startDate", "2026-10-21")
                        .param("endDate", "2026-10-22"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(scheduleTotalElements(range)).isEqualTo(baseline + 2);
        assertThat(scheduleBookingIds(range)).contains(dayOne, dayTwo);

        MvcResult singleDay = mockMvc.perform(get("/technicians/1/schedule")
                        .param("date", "2026-10-21")
                        .param("startDate", "2026-10-22")
                        .param("endDate", "2026-10-22"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(scheduleBookingIds(singleDay)).contains(dayTwo);
    }

    @Test
    void technicianScheduleInvalidDateRange() throws Exception {
        expectApiError(
                mockMvc.perform(get("/technicians/1/schedule")
                        .param("date", "2026-10-20")
                        .param("startDate", "2026-10-22")
                        .param("endDate", "2026-10-20")),
                400,
                "BAD_REQUEST",
                "VALIDATION_ERROR",
                "Parameter 'endDate': must be on or after startDate",
                "/technicians/1/schedule");
    }

    /** Default {@code sort=startTime,asc} orders earlier {@code startTime} before later on the same day. */
    @Test
    void technicianScheduleSort() throws Exception {
        String early = "2026-10-22T08:00:00+05:30";
        String late = "2026-10-22T16:00:00+05:30";
        createBooking("sched-sort-early", 1, "400001", early);
        createBooking("sched-sort-late", 1, "400001", late);

        MvcResult asc = mockMvc.perform(get("/technicians/1/schedule").param("date", "2026-10-22"))
                .andExpect(status().isOk())
                .andReturn();
        assertScheduleStartTimeOrder(asc, early, late, true);

        MvcResult desc = mockMvc.perform(get("/technicians/1/schedule")
                        .param("date", "2026-10-22")
                        .param("sort", "startTime,desc"))
                .andExpect(status().isOk())
                .andReturn();
        assertScheduleStartTimeOrder(desc, early, late, false);
    }

    @Test
    void failedBookingDoesNotBlockIdempotentRetryWithNewKey() throws Exception {
        String invalidSlot = """
                {"customerPhone":"9876543210","serviceId":1,"pincode":"400001","startTime":"2026-10-20T10:15:00+05:30"}
                """;
        expectApiError(
                mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "fail-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidSlot)),
                400,
                "BAD_REQUEST",
                "INVALID_SLOT",
                "Start time must align to 30-minute boundaries",
                "/bookings");

        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "success-after-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(1, "400001", "2026-10-20T10:30:00+05:30")))
                .andExpect(status().isCreated());
    }

    @Test
    void outsideHorizonReturnsBadRequest() throws Exception {
        expectApiError(
                mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "horizon-fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(1, "400001", "2026-10-15T10:00:00+05:30"))),
                400,
                "BAD_REQUEST",
                "OUTSIDE_HORIZON",
                "Booking must be between 2 hours and 7 days from now",
                "/bookings");
    }

    @Test
    void rejectsBookingThatViolatesTravelBuffer() throws Exception {
        mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "buffer-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(2, "400058", "2026-10-20T10:00:00+05:30")))
                .andExpect(status().isCreated());

        expectApiError(
                mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "buffer-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(2, "400058", "2026-10-20T10:30:00+05:30"))),
                409,
                "CONFLICT",
                "NO_TECHNICIAN_AVAILABLE",
                "No technician is available for the requested slot.",
                "/bookings");
    }

    @Test
    void missingIdempotencyKeyReturnsValidationError() throws Exception {
        expectApiError(
                mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(1, "400001", "2026-10-20T10:30:00+05:30"))),
                400,
                "BAD_REQUEST",
                "VALIDATION_ERROR",
                "Header 'Idempotency-Key' is required",
                "/bookings");
    }

    @Test
    void missingAvailabilityQueryParamReturnsInvalidParameter() throws Exception {
        expectApiError(
                mockMvc.perform(get("/availability").param("pincode", "400001").param("date", "2026-10-20")),
                400,
                "BAD_REQUEST",
                "INVALID_PARAMETER",
                "Parameter 'serviceId': is required",
                "/availability");
    }

    @Test
    void invalidAvailabilityDateReturnsInvalidParameter() throws Exception {
        expectApiError(
                mockMvc.perform(get("/availability")
                        .param("serviceId", "1")
                        .param("pincode", "400001")
                        .param("date", "not-a-date")),
                400,
                "BAD_REQUEST",
                "INVALID_PARAMETER",
                "Parameter 'date': invalid value 'not-a-date'",
                "/availability");
    }

    @Test
    void malformedJsonBodyReturnsMalformedRequest() throws Exception {
        expectApiError(
                mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "bad-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json")),
                400,
                "BAD_REQUEST",
                "MALFORMED_REQUEST",
                "Request body is malformed or unreadable",
                "/bookings");
    }

    @Test
    void invalidPhoneReturnsValidationError() throws Exception {
        String body = """
                {"customerPhone":"123","serviceId":1,"pincode":"400001","startTime":"2026-10-20T10:30:00+05:30"}
                """;
        expectApiError(
                mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "bad-phone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)),
                400,
                "BAD_REQUEST",
                "VALIDATION_ERROR",
                "Field 'customerPhone': must be a valid Indian mobile number",
                "/bookings");
    }

    @Test
    void assignsTechnicianWithFewerBookingsAndTieBreaksById() throws Exception {
        MvcResult first = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "assign-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(1, "400001", "2026-10-20T10:00:00+05:30")))
                .andExpect(status().isCreated())
                .andReturn();
        long firstTech = ((Number) com.jayway.jsonpath.JsonPath.read(
                        first.getResponse().getContentAsString(), "$.technicianId"))
                .longValue();
        assertThat(firstTech).isEqualTo(1L);
        MvcResult second = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", "assign-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(1, "400001", "2026-10-20T14:00:00+05:30")))
                .andExpect(status().isCreated())
                .andReturn();
        long secondTech = ((Number) com.jayway.jsonpath.JsonPath.read(
                        second.getResponse().getContentAsString(), "$.technicianId"))
                .longValue();
        assertThat(secondTech).isEqualTo(3L);
    }

    /** Creates a booking through {@code POST /bookings} and returns the new id. */
    private long createBooking(String idempotencyKey, long serviceId, String pincode, String startTime)
            throws Exception {
        return createBookingWithPhone(idempotencyKey, "9876543210", serviceId, pincode, startTime);
    }

    private long createBookingWithPhone(
            String idempotencyKey, String phone, long serviceId, String pincode, String startTime)
            throws Exception {
        MvcResult created = mockMvc.perform(post("/bookings")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(phone, serviceId, pincode, startTime)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id"))
                .longValue();
    }

    private static String bookingBody(long serviceId, String pincode, String startTime) {
        return bookingBody("9876543210", serviceId, pincode, startTime);
    }

    private static String bookingBody(String phone, long serviceId, String pincode, String startTime) {
        return String.format(
                "{\"customerPhone\":\"%s\",\"serviceId\":%d,\"pincode\":\"%s\",\"startTime\":\"%s\"}",
                phone, serviceId, pincode, startTime);
    }

    private int scheduleTotalElements(String technicianId, String anchorDate, String startDate, String endDate)
            throws Exception {
        MvcResult result = mockMvc.perform(get("/technicians/" + technicianId + "/schedule")
                        .param("date", anchorDate)
                        .param("startDate", startDate)
                        .param("endDate", endDate))
                .andExpect(status().isOk())
                .andReturn();
        return scheduleTotalElements(result);
    }

    private static int scheduleTotalElements(MvcResult result) throws Exception {
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                        result.getResponse().getContentAsString(), "$.bookings.totalElements"))
                .intValue();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> scheduleBookingIds(MvcResult result) throws Exception {
        List<Number> ids = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.bookings.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    @SuppressWarnings("unchecked")
    private static void assertScheduleStartTimeOrder(
            MvcResult result, String earlierStart, String laterStart, boolean ascending) throws Exception {
        List<String> times = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.bookings.content[*].startTime");
        int earlierIndex = times.indexOf(earlierStart);
        int laterIndex = times.indexOf(laterStart);
        assertThat(earlierIndex).isGreaterThanOrEqualTo(0);
        assertThat(laterIndex).isGreaterThanOrEqualTo(0);
        if (ascending) {
            assertThat(earlierIndex).isLessThan(laterIndex);
        } else {
            assertThat(laterIndex).isLessThan(earlierIndex);
        }
    }
}
