package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.CrmTaskPriority;
import com.aicrm.crm.domain.CrmTaskSource;
import com.aicrm.crm.domain.CrmTaskStatus;
import com.aicrm.crm.domain.CrmTaskType;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** CRM 任務 service 整合測試，釘住建立、延期、完成與時間規則。 */
class CrmTaskServiceTest extends PostgresTestBase {

    @Autowired CrmTaskService service;
    @Autowired CustomerRepository customers;
    @Autowired AppUserRepository users;

    /** 延期開放任務應保留 OPEN 並累加延期次數。 */
    @Test
    void postpone_openTask_movesScheduleAndIncrementsCounter() {
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = customers.findAll().stream().filter(c -> c.getOwner() != null).findFirst().orElseThrow();
        var start = LocalDateTime.of(2026, 8, 10, 9, 0);
        var principal = new JwtService.AuthPrincipal(owner.getUsername(), owner.getDisplayName(), Role.SALES);
        var task = service.create(principal, request(customer.getId(), owner.getId(), start));

        var updated = service.postpone(principal, task.id(),
                new Dtos.PostponeTaskRequest(start.plusDays(2), start.plusDays(2).plusHours(1)));

        assertThat(updated.postponeCount()).isEqualTo(1);
        assertThat(updated.status()).isEqualTo(CrmTaskStatus.OPEN);
        assertThat(updated.scheduledStart()).isEqualTo(start.plusDays(2));
    }

    /** 完成任務後不得再次延期。 */
    @Test
    void postpone_completedTask_isRejected() {
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = customers.findAll().stream().filter(c -> c.getOwner() != null).findFirst().orElseThrow();
        var start = LocalDateTime.of(2026, 8, 11, 9, 0);
        var principal = new JwtService.AuthPrincipal(owner.getUsername(), owner.getDisplayName(), Role.SALES);
        var task = service.create(principal, request(customer.getId(), owner.getId(), start));
        service.complete(principal, task.id());

        assertThatThrownBy(() -> service.postpone(principal, task.id(),
                new Dtos.PostponeTaskRequest(start.plusDays(1), start.plusDays(1).plusHours(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 建立任務時結束時間不得早於或等於開始時間。 */
    @Test
    void create_invalidSchedule_isRejected() {
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = customers.findAll().stream().filter(c -> c.getOwner() != null).findFirst().orElseThrow();
        var start = LocalDateTime.of(2026, 8, 12, 9, 0);
        var principal = new JwtService.AuthPrincipal(owner.getUsername(), owner.getDisplayName(), Role.SALES);
        var invalid = new Dtos.CreateTaskRequest(customer.getId(), null, null, CrmTaskType.PHONE_CALL,
                CrmTaskPriority.HIGH, "電話追蹤", "確認需求", owner.getId(), start, start, CrmTaskSource.MANUAL);

        assertThatThrownBy(() -> service.create(principal, invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 編輯帶入過期版本時應拒絕，避免覆蓋其他使用者變更。 */
    @Test
    void update_staleVersion_isRejected() {
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = customers.findAll().stream().filter(c -> c.getOwner() != null).findFirst().orElseThrow();
        var start = LocalDateTime.of(2026, 8, 13, 9, 0);
        var principal = new JwtService.AuthPrincipal(owner.getUsername(), owner.getDisplayName(), Role.SALES);
        var task = service.create(principal, request(customer.getId(), owner.getId(), start));
        var stale = new Dtos.UpdateTaskRequest(CrmTaskType.PHONE_CALL, CrmTaskPriority.HIGH, "更新標題", null,
                owner.getId(), start.plusHours(1), start.plusHours(2), task.version() + 1);

        assertThatThrownBy(() -> service.update(principal, task.id(), stale))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    /** 建立標準電話任務請求。 */
    private Dtos.CreateTaskRequest request(Long customerId, Long ownerId, LocalDateTime start) {
        return new Dtos.CreateTaskRequest(customerId, null, null, CrmTaskType.PHONE_CALL,
                CrmTaskPriority.HIGH, "電話追蹤", "確認需求", ownerId, start, start.plusHours(1), CrmTaskSource.MANUAL);
    }
}
