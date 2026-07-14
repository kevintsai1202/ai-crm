package com.aicrm.crm.api;

import com.aicrm.crm.service.CrmTaskService;
import com.aicrm.crm.service.IcsCalendarService;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** CRM 任務 HTTP 邊界，業務規則與 owner scope 由 CrmTaskService 統一執行。 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    /** 任務應用服務。 */ private final CrmTaskService tasks;
    /** iCalendar 產生服務。 */ private final IcsCalendarService calendars;

    public TaskController(CrmTaskService tasks, IcsCalendarService calendars) { this.tasks = tasks; this.calendars = calendars; }

    /** 列出登入者 scope 內任務。 */
    @GetMapping public List<Dtos.TaskResponse> list(@AuthenticationPrincipal AuthPrincipal principal) { return tasks.list(principal); }

    /** 建立 CRM 任務。 */
    @PostMapping public ResponseEntity<Dtos.TaskResponse> create(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody Dtos.CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tasks.create(principal, request));
    }

    /** 取得 scope 內單一任務。 */
    @GetMapping("/{id}") public Dtos.TaskResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) { return tasks.get(principal, id); }

    /** 編輯 scope 內任務。 */
    @PutMapping("/{id}") public Dtos.TaskResponse update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody Dtos.UpdateTaskRequest request) { return tasks.update(principal, id, request); }

    /** 完成 scope 內任務。 */
    @PostMapping("/{id}/complete") public Dtos.TaskResponse complete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody Dtos.CompleteTaskRequest request) { return tasks.complete(principal, id, request); }

    /** 延後 scope 內任務。 */
    @PostMapping("/{id}/postpone") public Dtos.TaskResponse postpone(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody Dtos.PostponeTaskRequest request) { return tasks.postpone(principal, id, request); }

    /** 顯式刪除 scope 內單一任務；version 防止刪除過期畫面所指的版本。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                       @RequestParam Long version) {
        tasks.delete(principal, id, version);
    }

    /** 即時產生 UTF-8 iCalendar 附件，不保存衍生檔案。 */
    @GetMapping("/{id}/calendar.ics")
    public ResponseEntity<byte[]> calendar(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        var task = tasks.get(principal, id);
        return ResponseEntity.ok().contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=crm-task-" + id + ".ics")
                .body(calendars.render(task));
    }
}
