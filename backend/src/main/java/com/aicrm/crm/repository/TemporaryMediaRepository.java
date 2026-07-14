package com.aicrm.crm.repository;

import com.aicrm.crm.domain.MediaStatus;
import com.aicrm.crm.domain.TemporaryMedia;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 暫存媒體 metadata 存取介面。 */
public interface TemporaryMediaRepository extends JpaRepository<TemporaryMedia, Long> {
    /** 取得已到期且仍可清理的媒體，confirmed/processing/review pending 不在清理範圍。 */
    @Query("select m from TemporaryMedia m where m.expiresAt <= :now and m.status in :statuses order by m.id")
    List<TemporaryMedia> findCleanupCandidates(@Param("now") Instant now, @Param("statuses") Collection<MediaStatus> statuses);
}
