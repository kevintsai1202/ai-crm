package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.CrmTaskPriority;
import com.aicrm.crm.domain.CrmTaskSource;
import com.aicrm.crm.domain.CrmTaskStatus;
import com.aicrm.crm.domain.CrmTaskType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** iCalendar 產生器單元測試，驗證 UTF-8、跳脫、CRLF 與穩定 UID。 */
class IcsCalendarServiceTest {

    private final IcsCalendarService service = new IcsCalendarService();

    /** 特殊字元需依 RFC 5545 跳脫，且相同任務 UID 必須穩定。 */
    @Test
    void render_specialCharacters_usesCrlfEscapesAndStableUid() {
        var task = new Dtos.TaskResponse(42L, 1L, null, null, CrmTaskType.MEETING, CrmTaskStatus.OPEN,
                CrmTaskPriority.HIGH, "會議,確認;需求\\版本", "第一行\n第二行", 7L, "王小明",
                LocalDateTime.of(2026, 8, 1, 9, 0), LocalDateTime.of(2026, 8, 1, 10, 0),
                null, 0, CrmTaskSource.MANUAL, 0L);

        var first = new String(service.render(task), StandardCharsets.UTF_8);
        var second = new String(service.render(task), StandardCharsets.UTF_8);

        assertThat(first).isEqualTo(second).contains("UID:crm-task-42@ai-crm")
                .contains("DTSTART;TZID=Asia/Taipei:20260801T090000")
                .contains("SUMMARY:會議\\,確認\\;需求\\\\版本")
                .contains("DESCRIPTION:第一行\\n第二行");
        assertThat(first.replace("\r\n", "")).doesNotContain("\n");
        assertThat(first).endsWith("END:VCALENDAR\r\n");
    }
}
