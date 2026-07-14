package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 由 CRM 任務即時產生 RFC 5545 iCalendar，不保存衍生檔案。 */
@Service
public class IcsCalendarService {
    /** iCalendar local date-time 格式。 */
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    /** UTC DTSTAMP 格式，revisionTimestamp 讓同一版本輸出保持穩定。 */
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** 產生 UTF-8、CRLF、Asia/Taipei 時區的單事件行事曆。 */
    public byte[] render(Dtos.TaskResponse task) {
        var logicalLines = List.of(
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//AI CRM//CRM Task//ZH-TW", "CALSCALE:GREGORIAN",
                "BEGIN:VTIMEZONE", "TZID:Asia/Taipei", "X-LIC-LOCATION:Asia/Taipei",
                "BEGIN:STANDARD", "TZOFFSETFROM:+0800", "TZOFFSETTO:+0800", "TZNAME:CST", "DTSTART:19700101T000000",
                "END:STANDARD", "END:VTIMEZONE", "BEGIN:VEVENT", "UID:crm-task-" + task.id() + "@ai-crm",
                "DTSTAMP:" + UTC_FORMAT.format(task.revisionTimestamp()),
                "DTSTART;TZID=Asia/Taipei:" + FORMAT.format(task.scheduledStart()),
                "DTEND;TZID=Asia/Taipei:" + FORMAT.format(task.scheduledEnd()),
                "SUMMARY:" + escape(task.title()), "DESCRIPTION:" + escape(task.description()),
                "STATUS:" + (task.status() == com.aicrm.crm.domain.CrmTaskStatus.COMPLETED ? "COMPLETED" : "CONFIRMED"),
                "END:VEVENT", "END:VCALENDAR");
        var physicalLines = new ArrayList<String>();
        logicalLines.forEach(line -> physicalLines.addAll(fold(line)));
        return (String.join("\r\n", physicalLines) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /** 跳脫 RFC 5545 text 中的反斜線、換行、逗號與分號。 */
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n")
                .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }

    /** 依 RFC 5545 將 content line 折為最多 75 UTF-8 octets，續行首空白計入上限。 */
    private List<String> fold(String line) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        int currentBytes = 0;
        int limit = 75;
        for (int offset = 0; offset < line.length();) {
            int codePoint = line.codePointAt(offset);
            var character = new String(Character.toChars(codePoint));
            int bytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes + bytes > limit && !current.isEmpty()) {
                result.add(current.toString());
                current = new StringBuilder(" ");
                currentBytes = 1;
                limit = 75;
            }
            current.append(character);
            currentBytes += bytes;
            offset += Character.charCount(codePoint);
        }
        result.add(current.toString());
        return result;
    }
}
