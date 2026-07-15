package com.aicrm.crm.repository;

import com.aicrm.crm.domain.CrmTask;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** CRM 任務資料存取介面。 */
public interface CrmTaskRepository extends JpaRepository<CrmTask, Long> {
    /** 依負責人帳號查詢任務。 */
    List<CrmTask> findByAssigneeUsernameOrderByScheduledStartAsc(String username);
    /** 管理角色查詢全部任務。 */
    List<CrmTask> findAllByOrderByScheduledStartAsc();

    /** 查詢指定商機的所有任務（供 V26 健康度計算未完成/逾期任務數）。 */
    List<CrmTask> findByOpportunityId(Long opportunityId);
}
