package com.booking.service;

import com.booking.dto.BookingResponse;
import com.booking.entity.IdempotencyRecordEntity;
import com.booking.exception.ApiException;
import com.booking.exception.ErrorCode;
import com.booking.repository.IdempotencyRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final BookingMapper mapper;

    public IdempotencyService(IdempotencyRecordRepository repository, BookingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public BookingService.BookingResult findResult(String key, String hash) {
        var rec = repository.findByIdempotencyKey(key);
        if (rec.isEmpty()) {
            return null;
        }
        return toResult(rec.get(), hash);
    }

    private BookingService.BookingResult toResult(IdempotencyRecordEntity rec, String hash) {
        if (!rec.getRequestHash().equals(hash)) {
            throw new ApiException(
                    HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency key reused with different payload");
        }
        if (rec.getBooking() == null) {
            return null;
        }
        return new BookingService.BookingResult(mapper.toResponse(rec.getBooking()), false);
    }
}
