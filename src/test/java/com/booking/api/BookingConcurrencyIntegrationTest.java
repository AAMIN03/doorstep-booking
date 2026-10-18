package com.booking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.booking.TestClockConfig;
import com.booking.TestcontainersConfiguration;
import com.booking.enums.BookingStatus;
import com.booking.repository.BookingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClockConfig.class })
class BookingConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void concurrentDifferentKeysOnlyOneBookingSucceeds() throws Exception {
        String body = """
                {"customerPhone":"9876543210","serviceId":1,"pincode":"400058","startTime":"2026-10-20T15:00:00+05:30"}
                """;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String key = "race-" + i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                MvcResult result = mockMvc.perform(post("/bookings").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
                return result.getResponse().getStatus();
            }));
        }
        ready.await();
        start.countDown();
        int created = 0;
        int failed = 0;
        for (Future<Integer> f : futures) {
            int status = f.get();
            if (status == 201) {
                created++;
            } else if (status == 409) {
                failed++;
            }
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(failed).isEqualTo(1);
        Instant slot = Instant.parse("2026-10-20T09:30:00Z");
        long confirmedAtSlot = bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED).filter(b -> b.getStartTime().equals(slot))
                .count();
        assertThat(confirmedAtSlot).isEqualTo(1);
    }

    @Test
    void concurrentSameIdempotencyKeyCreatesSingleBooking() throws Exception {
        String body = """
                {"customerPhone":"9876543210","serviceId":3,"pincode":"400003","startTime":"2026-10-20T16:00:00+05:30"}
                """;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                for (int attempt = 0; attempt < 40; attempt++) {
                    MvcResult result = mockMvc.perform(post("/bookings").header("Idempotency-Key", "same-key")
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 201 || status == 200) {
                        return ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                                "$.id")).longValue();
                    }
                    Thread.sleep(25);
                }
                throw new IllegalStateException("Idempotent booking not ready");
            }));
        }
        ready.await();
        start.countDown();
        Long id1 = futures.get(0).get();
        Long id2 = futures.get(1).get();
        pool.shutdown();
        assertThat(id1).isEqualTo(id2);
        long bookingsForSlot = bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .filter(b -> b.getStartTime().equals(Instant.parse("2026-10-20T10:30:00Z"))).count();
        assertThat(bookingsForSlot).isEqualTo(1);
    }
}
