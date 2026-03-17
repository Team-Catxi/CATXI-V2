package com.project.catxi.report.service;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.CommonErrorCode;
import com.project.catxi.common.api.error.ReportErrorCode;
import com.project.catxi.common.api.handler.ReportExceptionHandler;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.report.domain.Report;
import com.project.catxi.report.dto.ReportCreateReq;
import com.project.catxi.report.dto.ReportCreateRes;
import com.project.catxi.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final DiscordWebhookService discordWebhookService;

    @Transactional
    public ReportCreateRes createReport(Long roomId, String targetUserEmail, String reporterEmail, ReportCreateReq req) {
        Member reporter = memberRepository.findByEmail(reporterEmail)
            .orElseThrow(() -> new ReportExceptionHandler(CommonErrorCode.USER_NOT_FOUND));

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
            .orElseThrow(() -> new ReportExceptionHandler(CommonErrorCode.RESOURCE_NOT_FOUND));

        Member reportedMember = memberRepository.findByEmail(targetUserEmail)
            .orElseThrow(() -> new ReportExceptionHandler(CommonErrorCode.RESOURCE_NOT_FOUND));

        validateReport(chatRoom, reporter, reportedMember);

        Report report = Report.builder()
            .roomId(roomId)
            .reporter(reporter)
            .reportedMember(reportedMember)
            .reason(req.reason())
            .build();

        Report savedReport = reportRepository.save(report);
        discordWebhookService.sendReportNotification(savedReport);

        return ReportCreateRes.from(savedReport);
    }

    private void validateReport(ChatRoom room, Member reporter, Member reportedMember) {
        if (reporter.getId().equals(reportedMember.getId())) {
            throw new ReportExceptionHandler(ReportErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        if (!chatParticipantRepository.existsByChatRoomAndMember(room, reporter)) {
            throw new ReportExceptionHandler(ReportErrorCode.NOT_A_CHAT_PARTICIPANT);
        }

        if (!chatParticipantRepository.existsByChatRoomAndMember(room, reportedMember)) {
            throw new ReportExceptionHandler(ReportErrorCode.REPORTED_MEMBER_NOT_IN_ROOM);
        }

        reportRepository.findDuplicateReport(room.getRoomId(), reporter.getId(), reportedMember.getId())
            .ifPresent(report -> {
                throw new ReportExceptionHandler(ReportErrorCode.ALREADY_REPORTED);
            });
    }
}
