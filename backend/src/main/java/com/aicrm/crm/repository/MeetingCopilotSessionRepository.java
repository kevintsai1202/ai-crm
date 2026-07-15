package com.aicrm.crm.repository;

import com.aicrm.crm.domain.MeetingCopilotSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** 會議 Copilot session 資料存取。 */
public interface MeetingCopilotSessionRepository extends JpaRepository<MeetingCopilotSession, Long> {
    /** 以悲觀鎖序列化同一 session 的併發確認。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MeetingCopilotSession s where s.id=:id")
    Optional<MeetingCopilotSession> findByIdForUpdate(@Param("id") Long id);

    /** 查詢此使用者首次使用冪等鍵產生的確認結果。 */
    Optional<MeetingCopilotSession> findByCreatorUsernameAndIdempotencyKey(String creatorUsername, String idempotencyKey);
}
