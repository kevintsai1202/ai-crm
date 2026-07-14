package com.aicrm.crm.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aicrm.crm.domain.*;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
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
    private final ObjectMapper om = new ObjectMapper();
    private MockMvc mvc;
    private String salesToken;
    private String managerToken;

    /** 每個測試前建立套用 security filter 的 MockMvc 並登入。 */
    @BeforeEach
    void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        salesToken = login("sales@aurora.local");
        managerToken = login("manager@aurora.local");
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

    /** 以固定排程建立測試任務請求。 */
    private Dtos.CreateTaskRequest request(Long customerId, Long assigneeId, String title) {
        return new Dtos.CreateTaskRequest(customerId, null, null, CrmTaskType.GENERAL, CrmTaskPriority.NORMAL,
                title, null, assigneeId, LocalDateTime.of(2026, 9, 2, 9, 0),
                LocalDateTime.of(2026, 9, 2, 10, 0), CrmTaskSource.MANUAL);
    }

    /** 登入 seed 帳號並回傳 JWT。 */
    private String login(String username) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(response).get("token").asText();
    }
}
