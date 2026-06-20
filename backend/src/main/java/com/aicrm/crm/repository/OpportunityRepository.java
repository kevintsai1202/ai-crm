package com.aicrm.crm.repository;

import com.aicrm.crm.domain.Opportunity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 商機資料存取介面。
 */
public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {
}

