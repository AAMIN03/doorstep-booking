package com.booking.repository;

import com.booking.entity.IdempotencyRecordEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, Long> {

    @Query("""
            select r from IdempotencyRecordEntity r
            left join fetch r.booking b
            left join fetch b.service
            left join fetch b.technician
            where r.idempotencyKey = :key
            """)
    Optional<IdempotencyRecordEntity> findByIdempotencyKey(@Param("key") String idempotencyKey);
}
