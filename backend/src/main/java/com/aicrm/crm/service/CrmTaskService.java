package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.*;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRM 任務應用服務，集中處理 scope、關聯一致性、狀態與時間規則。 */
@Service
@Transactional
public class CrmTaskService {
    /** 任務儲存庫。 */ private final CrmTaskRepository tasks;
    /** 客戶儲存庫。 */ private final CustomerRepository customers;
    /** 商機儲存庫。 */ private final OpportunityRepository opportunities;
    /** 聯絡人儲存庫。 */ private final ContactRepository contacts;
    /** 帳號儲存庫。 */ private final AppUserRepository users;

    public CrmTaskService(CrmTaskRepository tasks, CustomerRepository customers,
                          OpportunityRepository opportunities, ContactRepository contacts, AppUserRepository users) {
        this.tasks = tasks; this.customers = customers; this.opportunities = opportunities;
        this.contacts = contacts; this.users = users;
    }

    /** 依角色列出可見任務；SALES 強制只查自己的帳號。 */
    @Transactional(readOnly = true)
    public List<Dtos.TaskResponse> list(AuthPrincipal principal) {
        var visible = principal.role() == Role.SALES
                ? tasks.findByAssigneeUsernameOrderByScheduledStartAsc(principal.username())
                : tasks.findAllByOrderByScheduledStartAsc();
        return visible.stream().map(this::toResponse).toList();
    }

    /** 取得 scope 內的單一任務，跨 owner 依既有 IDOR 慣例回 403。 */
    @Transactional(readOnly = true)
    public Dtos.TaskResponse get(AuthPrincipal principal, Long id) { return toResponse(findVisible(principal, id)); }

    /** 建立任務並驗證關聯皆屬同一客戶。 */
    public Dtos.TaskResponse create(AuthPrincipal principal, Dtos.CreateTaskRequest request) {
        validateSchedule(request.scheduledStart(), request.scheduledEnd());
        var customer = customers.findById(request.customerId())
                .orElseThrow(() -> new EntityNotFoundException("查無此客戶資料：" + request.customerId()));
        var assignee = users.findById(request.assigneeId())
                .orElseThrow(() -> new EntityNotFoundException("查無此帳號：" + request.assigneeId()));
        assertCanAssign(principal, assignee);
        var opportunity = request.opportunityId() == null ? null : opportunities.findById(request.opportunityId())
                .orElseThrow(() -> new EntityNotFoundException("查無此商機：" + request.opportunityId()));
        var contact = request.contactId() == null ? null : contacts.findById(request.contactId())
                .orElseThrow(() -> new EntityNotFoundException("查無此聯絡人：" + request.contactId()));
        if (opportunity != null && !opportunity.getCustomer().getId().equals(customer.getId())) throw new com.aicrm.crm.service.task.TaskValidationException("商機不屬於指定客戶");
        if (contact != null && !contact.getCustomer().getId().equals(customer.getId())) throw new com.aicrm.crm.service.task.TaskValidationException("聯絡人不屬於指定客戶");
        var task = new CrmTask(customer, opportunity, contact, request.type(), request.priority(), request.title().trim(),
                request.description(), assignee, request.scheduledStart(), request.scheduledEnd(), request.source());
        return toResponse(tasks.save(task));
    }

    /** 編輯 scope 內尚未結束的任務，並檢查用戶端版本。 */
    public Dtos.TaskResponse update(AuthPrincipal principal, Long id, Dtos.UpdateTaskRequest request) {
        validateSchedule(request.scheduledStart(), request.scheduledEnd());
        var task = findVisible(principal, id);
        assertVersion(task, request.version());
        var assignee = users.findById(request.assigneeId()).orElseThrow(() -> new EntityNotFoundException("查無此帳號：" + request.assigneeId()));
        assertCanAssign(principal, assignee);
        task.update(request.type(), request.priority(), request.title().trim(), request.description(), assignee,
                request.scheduledStart(), request.scheduledEnd());
        return toResponse(saveMutation(task));
    }

    /** 延期 scope 內尚未結束的任務。 */
    public Dtos.TaskResponse postpone(AuthPrincipal principal, Long id, Dtos.PostponeTaskRequest request) {
        validateSchedule(request.scheduledStart(), request.scheduledEnd());
        var task = findVisible(principal, id);
        assertVersion(task, request.version());
        task.postpone(request.scheduledStart(), request.scheduledEnd());
        return toResponse(saveMutation(task));
    }

    /** 完成 scope 內尚未結束的任務。 */
    public Dtos.TaskResponse complete(AuthPrincipal principal, Long id, Dtos.CompleteTaskRequest request) {
        var task = findVisible(principal, id);
        assertVersion(task, request.version());
        task.complete(LocalDateTime.now());
        return toResponse(saveMutation(task));
    }

    /** 驗證 SALES 只能指派自己；管理角色沿用既有全團隊可見性。 */
    private void assertCanAssign(AuthPrincipal principal, AppUser assignee) {
        if (principal.role() == Role.SALES && !principal.username().equals(assignee.getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("無權指派任務給其他業務");
        }
    }

    /** 載入任務並套用既有 SALES 跨 owner 回 403 慣例。 */
    private CrmTask findVisible(AuthPrincipal principal, Long id) {
        var task = tasks.findById(id).orElseThrow(() -> new EntityNotFoundException("查無此任務：" + id));
        if (principal.role() == Role.SALES && !principal.username().equals(task.getAssignee().getUsername())) {
            throw new org.springframework.security.access.AccessDeniedException("無權存取非本人負責的任務");
        }
        return task;
    }

    /** 驗證排程結束時間嚴格晚於開始時間。 */
    private void validateSchedule(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) throw new com.aicrm.crm.service.task.TaskValidationException("任務結束時間必須晚於開始時間");
    }

    /** mutation 前比對 client version，拒絕順序式 stale UI。 */
    private void assertVersion(CrmTask task, Long requestedVersion) {
        if (requestedVersion == null || task.getVersion() != requestedVersion) {
            throw new com.aicrm.crm.service.task.TaskConflictException();
        }
    }

    /** 將 Task 的資料庫競爭式 optimistic lock 轉為 Task 專屬安全衝突。 */
    private CrmTask saveMutation(CrmTask task) {
        try {
            return tasks.saveAndFlush(task);
        } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
            throw new com.aicrm.crm.service.task.TaskConflictException();
        }
    }

    /** 將任務實體轉為 API DTO。 */
    private Dtos.TaskResponse toResponse(CrmTask task) {
        return new Dtos.TaskResponse(task.getId(), task.getCustomer().getId(),
                task.getOpportunity() == null ? null : task.getOpportunity().getId(),
                task.getContact() == null ? null : task.getContact().getId(), task.getType(), task.getStatus(),
                task.getPriority(), task.getTitle(), task.getDescription(), task.getAssignee().getId(),
                task.getAssignee().getDisplayName(), task.getScheduledStart(), task.getScheduledEnd(),
                task.getCompletedAt(), task.getPostponeCount(), task.getSource(), task.getVersion(),
                task.getUpdatedAt() == null ? java.time.Instant.EPOCH : task.getUpdatedAt());
    }
}
