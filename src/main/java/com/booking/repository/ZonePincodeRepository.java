package com.booking.repository;

import com.booking.entity.ZonePincodeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZonePincodeRepository extends JpaRepository<ZonePincodeEntity, Long> {

    @Query("select zp from ZonePincodeEntity zp join fetch zp.zone where zp.pincode = :pincode")
    Optional<ZonePincodeEntity> findByPincodeWithZone(@Param("pincode") String pincode);
}
