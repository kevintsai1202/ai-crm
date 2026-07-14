package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

/** 由 CRM 任務即時產生 RFC 5545 iCalendar，不保存衍生檔案。 */
@Service
public class IcsCalendarService {
    /** iCalendar local date-time 格式。 */
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    /** 產生 UTF-8、CRLF、Asia/Taipei 時區的單事件行事曆。 */
    public byte[] render(Dtos.TaskResponse task) {
        var calendar = String.join("\r\n",
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//AI CRM//CRM Task//ZH-TW", "CALSCALE:GREGORIAN",
                "BEGIN:VEVENT", "UID:crm-task-" + task.id() + "@ai-crm",
                "DTSTART;TZID=Asia/Taipei:" + FORMAT.format(task.scheduledStart()),
                "DTEND;TZID=Asia/Taipei:" + FORMAT.format(task.scheduledEnd()),
                "SUMMARY:" + escape(task.title()), "DESCRIPTION:" + escape(task.description()),
                "STATUS:" + (task.status() == com.aicrm.crm.domain.CrmTaskStatus.COMPLETED ? "COMPLETED" : "CONFIRMED"),
                "END:VEVENT", "END:VCALENDAR", "");
        return calendar.getBytes(StandardCharsets.UTF_8);
    }

    /** 跳脫 RFC 5545 text 中的反斜線、換行、逗號與分號。 */
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n")
                .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }
}
