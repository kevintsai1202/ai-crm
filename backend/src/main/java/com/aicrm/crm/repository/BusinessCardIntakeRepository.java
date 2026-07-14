package com.aicrm.crm.repository;

import com.aicrm.crm.domain.BusinessCardIntake;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** 名片辨識工作資料存取。 */
public interface BusinessCardIntakeRepository extends JpaRepository<BusinessCardIntake, Long> {
    /** 以悲觀鎖序列化同一 intake 的併發確認。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BusinessCardIntake b where b.id=:id")
    Optional<BusinessCardIntake> findByIdForUpdate(@Param("id") Long id);
}
