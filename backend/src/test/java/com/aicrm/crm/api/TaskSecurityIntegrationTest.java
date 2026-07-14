package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import com.aicrm.crm.repository.ContactRepository;
import com.aicrm.crm.service.CrmTaskService;
import com.aicrm.crm.service.JwtService;
import com.aicrm.crm.support.PostgresTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** CRM Task API 的認證、owner scope 與行事曆下載整合測試。 */
class TaskSecurityIntegrationTest extends PostgresTestBase {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy springSecurityFilterChain;
    @Autowired CrmTaskService service;
    @Autowired AppUserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired OpportunityRepository opportunities;
    @Autowired ContactRepository contacts;
    private final ObjectMapper om = new ObjectMapper();
    private MockMvc mvc;
    private String salesToken;
    private String managerToken;
    private String adminToken;

    /** 每個測試前建立套用 security filter 的 MockMvc 並登入。 */
    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        salesToken = login("sales@aurora.local");
        managerToken = login("manager@aurora.local");
        adminToken = login("admin@aurora.local");
    }

    /** SALES 可建立指派給自己的任務。 */
    @Test
    void create_asSalesForSelf_returnsCreated() throws Exception {
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = customers.findAll().getFirst();
        var payload = "{\"customerId\":" + customer.getId() + ",\"type\":\"PHONE_CALL\",\"priority\":\"HIGH\"," +
                "\"title\":\"電話追蹤\",\"description\":\"確認需求\",\"assigneeId\":" + owner.getId() + "," +
                "\"scheduledStart\":\"2026-09-01T09:00:00\",\"scheduledEnd\":\"2026-09-01T10:00:00\",\"source\":\"MANUAL\"}";

        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + salesToken)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assigneeId").value(owner.getId()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    /** SALES 存取其他業務任務依既有 IDOR 慣例回 403，MANAGER 可存取同一任務。 */
    @Test
    void get_otherAssignee_salesForbiddenManagerAllowed() throws Exception {
        var other = users.findByRole(Role.SALES).stream()
                .filter(user -> !user.getUsername().equals("sales@aurora.local")).findFirst().orElseThrow();
        var customer = customers.findAll().getFirst();
        var task = service.create(new JwtService.AuthPrincipal("manager@aurora.local", "銷售經理", Role.MANAGER),
                request(customer.getId(), other.getId(), "跨業務測試"));

        mvc.perform(get("/api/tasks/{id}", task.id()).header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks/{id}", task.id()).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(task.id()));
    }

    /** 行事曆下載需提供 UTF-8 iCalendar 類型與安全檔名。 */
    @Test
    void calendar_returnsIcsHeadersAndBody() throws Exception {
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = customers.findAll().getFirst();
        var task = service.create(new JwtService.AuthPrincipal(owner.getUsername(), owner.getDisplayName(), Role.SALES),
                request(customer.getId(), owner.getId(), "calendar"));

        mvc.perform(get("/api/tasks/{id}/calendar.ics", task.id()).header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=crm-task-" + task.id() + ".ics"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UID:crm-task-" + task.id() + "@ai-crm")));
    }

    /** SALES 列表不得出現其他業務任務，MANAGER 與 ADMIN 可見。 */
    @Test
    void list_enforcesSalesScopeAndAllowsManagementRoles() throws Exception {
        var otherTask = createOtherAssigneeTask("列表 scope");
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + salesToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + otherTask.id() + ")]").isEmpty());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("列表 scope")));
        mvc.perform(get("/api/tasks/{id}", otherTask.id()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    /** SALES 不得藉 create payload 偽造 assignee。 */
    @Test
    void create_salesAssigneeSpoof_returnsForbidden() throws Exception {
        var other = users.findByRole(Role.SALES).stream().filter(u -> !u.getUsername().equals("sales@aurora.local")).findFirst().orElseThrow();
        var customer = customers.findAll().getFirst();
        var payload = taskJson(customer.getId(), other.getId(), null, null, "偽造 assignee");
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + salesToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
    }

    /** SALES 對其他 owner 的 update/postpone/complete 一律沿用 403。 */
    @Test
    void mutations_crossOwner_allReturnForbidden() throws Exception {
        var task = createOtherAssigneeTask("跨 owner mutation");
        var other = users.findById(task.assigneeId()).orElseThrow();
        var update = "{\"type\":\"GENERAL\",\"priority\":\"NORMAL\",\"title\":\"x\",\"assigneeId\":" + other.getId()
                + ",\"scheduledStart\":\"2026-09-03T09:00:00\",\"scheduledEnd\":\"2026-09-03T10:00:00\",\"version\":" + task.version() + "}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/tasks/{id}", task.id())
                .header("Authorization", "Bearer " + salesToken).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/{id}/postpone", task.id()).header("Authorization", "Bearer " + salesToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledStart\":\"2026-09-04T09:00:00\",\"scheduledEnd\":\"2026-09-04T10:00:00\",\"version\":" + task.version() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/{id}/complete", task.id()).header("Authorization", "Bearer " + salesToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + task.version() + "}"))
                .andExpect(status().isForbidden());
    }

    /** stale postpone/complete 應回固定安全 409，查無任務維持 404。 */
    @Test
    void staleMutations_returnSafeConflictAndMissingReturns404() throws Exception {
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var customer = customers.findAll().getFirst();
        var task = service.create(new JwtService.AuthPrincipal(owner.getUsername(), owner.getDisplayName(), Role.SALES), request(customer.getId(), owner.getId(), "stale"));
        var postpone = "{\"scheduledStart\":\"2026-09-05T09:00:00\",\"scheduledEnd\":\"2026-09-05T10:00:00\",\"version\":" + task.version() + "}";
        mvc.perform(post("/api/tasks/{id}/postpone", task.id()).header("Authorization", "Bearer " + salesToken)
                .contentType(MediaType.APPLICATION_JSON).content(postpone)).andExpect(status().isOk());
        mvc.perform(post("/api/tasks/{id}/complete", task.id()).header("Authorization", "Bearer " + salesToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + task.version() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("CRM 任務已被其他使用者更新，請重新載入"));
        mvc.perform(get("/api/tasks/999999999").header("Authorization", "Bearer " + managerToken)).andExpect(status().isNotFound());
    }

    /** opportunity/contact 必須屬於 request customer，錯配時回安全 400。 */
    @Test
    void create_crossCustomerRelations_returnsSafeBadRequest() throws Exception {
        var opportunity = opportunities.findAll().getFirst();
        var contact = contacts.findAll().stream().filter(c -> !c.getCustomer().getId().equals(opportunity.getCustomer().getId())).findFirst().orElseThrow();
        var owner = users.findByUsername("sales@aurora.local").orElseThrow();
        var payload = taskJson(contact.getCustomer().getId(), owner.getId(), opportunity.getId(), null, "錯配商機");
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + salesToken)
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("CRM 任務資料不符合規則"));
        var contactPayload = taskJson(opportunity.getCustomer().getId(), owner.getId(), null, contact.getId(), "錯配聯絡人");
        mvc.perform(post("/api/tasks").header("Authorization", "Bearer " + salesToken)
                .contentType(MediaType.APPLICATION_JSON).content(contactPayload))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("CRM 任務資料不符合規則"));
    }

    /** 以固定排程建立測試任務請求。 */
    private Dtos.CreateTaskRequest request(Long customerId, Long assigneeId, String title) {
        return new Dtos.CreateTaskRequest(customerId, null, null, CrmTaskType.GENERAL, CrmTaskPriority.NORMAL,
                title, null, assigneeId, LocalDateTime.of(2026, 9, 2, 9, 0),
                LocalDateTime.of(2026, 9, 2, 10, 0), CrmTaskSource.MANUAL);
    }

    /** 建立其他 SALES 負責的任務。 */
    private Dtos.TaskResponse createOtherAssigneeTask(String title) {
        var other = users.findByRole(Role.SALES).stream().filter(user -> !user.getUsername().equals("sales@aurora.local")).findFirst().orElseThrow();
        return service.create(new JwtService.AuthPrincipal("manager@aurora.local", "銷售經理", Role.MANAGER),
                request(customers.findAll().getFirst().getId(), other.getId(), title));
    }

    /** 組裝可選 opportunity/contact 的 create JSON。 */
    private String taskJson(Long customerId, Long assigneeId, Long opportunityId, Long contactId, String title) {
        return "{\"customerId\":" + customerId + (opportunityId == null ? "" : ",\"opportunityId\":" + opportunityId)
                + (contactId == null ? "" : ",\"contactId\":" + contactId)
                + ",\"type\":\"GENERAL\",\"priority\":\"NORMAL\",\"title\":\"" + title + "\",\"assigneeId\":" + assigneeId
                + ",\"scheduledStart\":\"2026-09-02T09:00:00\",\"scheduledEnd\":\"2026-09-02T10:00:00\",\"source\":\"MANUAL\"}";
    }

    /** 登入 seed 帳號並回傳 JWT。 */
    private String login(String username) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(response).get("token").asText();
    }
}
