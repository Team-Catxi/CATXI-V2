package com.project.catxi.report.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.project.catxi.report.domain.Report;
import java.time.LocalDateTime;

public record ReportCreateRes(
        Long reportId,
        Long reportedUserId,
        String reportedUserNickname,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime reportedAt
) {
    public static ReportCreateRes from(Report report) {
        return new ReportCreateRes(
                report.getId(),
                report.getReportedMember().getId(),
                report.getReportedMember().getNickname(),
                report.getCreatedTime()
        );
    }
}
