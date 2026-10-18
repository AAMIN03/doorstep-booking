package com.booking.repository;

import com.booking.entity.BookingEntity;
import com.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<BookingEntity, Long> {

    @Query("""
            select b from BookingEntity b
            join fetch b.service join fetch b.technician
            where b.id = :id
            """)
    java.util.Optional<BookingEntity> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from BookingEntity b
            join fetch b.service join fetch b.technician
            where b.id = :id
            """)
    java.util.Optional<BookingEntity> findDetailedByIdForUpdate(@Param("id") Long id);

    @Query("""
            select b from BookingEntity b
            join fetch b.service join fetch b.technician
            where b.id in :ids
            """)
    /** Loads bookings with service and technician for a page of ids (open-in-view is off). */
    List<BookingEntity> findDetailedByIdIn(@Param("ids") List<Long> ids);

    /** Paginated id query for {@code GET /bookings?phone=} with optional status and start-time range. */
    @Query(
            value = """
            select b.id from BookingEntity b
            where b.customerPhone = :phone
              and (:status is null or b.status = :status)
              and (:startInclusive is null or b.startTime >= :startInclusive)
              and (:endExclusive is null or b.startTime < :endExclusive)
            """,
            countQuery = """
            select count(b) from BookingEntity b
            where b.customerPhone = :phone
              and (:status is null or b.status = :status)
              and (:startInclusive is null or b.startTime >= :startInclusive)
              and (:endExclusive is null or b.startTime < :endExclusive)
            """)
    Page<Long> findIdsByCustomerPhone(
            @Param("phone") String phone,
            @Param("status") BookingStatus status,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive,
            Pageable pageable);

    /** Paginated id query for technician schedule with optional status and IST day range. */
    @Query(
            value = """
            select b.id from BookingEntity b
            where b.technician.id = :techId
              and b.startTime >= :dayStart
              and b.startTime < :dayEnd
              and (
                (:status is not null and b.status = :status)
                or (:status is null and b.status <> 'CANCELLED')
              )
            """,
            countQuery = """
            select count(b) from BookingEntity b
            where b.technician.id = :techId
              and b.startTime >= :dayStart
              and b.startTime < :dayEnd
              and (
                (:status is not null and b.status = :status)
                or (:status is null and b.status <> 'CANCELLED')
              )
            """)
    Page<Long> findIdsForTechnicianSchedule(
            @Param("techId") Long techId,
            @Param("status") BookingStatus status,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            Pageable pageable);

    @Query("""
            select b from BookingEntity b
            where b.technician.id in :techIds
              and b.status = 'CONFIRMED'
              and b.endTime > :rangeStart
              and b.startTime < :rangeEnd
            """)
    List<BookingEntity> findActiveInRange(
            @Param("techIds") List<Long> techIds,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from BookingEntity b
            where b.technician.id in :techIds
              and b.status = 'CONFIRMED'
              and b.endTime > :rangeStart
              and b.startTime < :rangeEnd
            """)
    List<BookingEntity> findActiveInRangeForUpdate(
            @Param("techIds") List<Long> techIds,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd);

    @Query("""
            select b from BookingEntity b
            where b.technician.id = :techId
              and b.status <> 'CANCELLED'
              and b.startTime >= :dayStart
              and b.startTime < :dayEnd
            order by b.startTime
            """)
    List<BookingEntity> findScheduleForDay(
            @Param("techId") Long techId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd);

    @Query("""
            select count(b) from BookingEntity b
            where b.technician.id = :techId
              and b.status <> :cancelled
              and b.startTime >= :dayStart
              and b.startTime < :dayEnd
            """)
    long countActiveForDay(
            @Param("techId") Long techId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd,
            @Param("cancelled") BookingStatus cancelled);
}
