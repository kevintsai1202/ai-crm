package com.aicrm.crm.repository;

import com.aicrm.crm.domain.OutboundEmail;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** 外寄郵件資料存取。 */
public interface OutboundEmailRepository extends JpaRepository<OutboundEmail, Long> {
    /** 以悲觀鎖序列化同一封郵件的併發重試。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboundEmail e where e.id=:id")
    Optional<OutboundEmail> findByIdForUpdate(@Param("id") Long id);

    /** 查詢此使用者首次使用寄送冪等鍵產生的外寄郵件。 */
    Optional<OutboundEmail> findByCreatorUsernameAndIdempotencyKey(String creatorUsername, String idempotencyKey);
}
