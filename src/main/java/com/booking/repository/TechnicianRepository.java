package com.booking.repository;

import com.booking.entity.TechnicianEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechnicianRepository extends JpaRepository<TechnicianEntity, Long> {

    @Query("""
            select distinct t from TechnicianEntity t
            join fetch t.zone
            join t.skills s
            where t.zone.id = :zoneId and s = :skill
            order by t.id
            """)
    List<TechnicianEntity> findEligible(@Param("zoneId") Long zoneId, @Param("skill") com.booking.enums.Skill skill);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TechnicianEntity t join fetch t.zone where t.id in :ids order by t.id")
    List<TechnicianEntity> lockByIdsInOrder(@Param("ids") Collection<Long> ids);

}
