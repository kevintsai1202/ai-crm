package com.aicrm.crm.repository;

import com.aicrm.crm.domain.FollowUpDraft;
import org.springframework.data.jpa.repository.JpaRepository;

/** 跟進信草稿資料存取。 */
public interface FollowUpDraftRepository extends JpaRepository<FollowUpDraft, Long> {
}
