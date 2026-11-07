package kr.securityadvisor.assignment.controller;

import java.util.List;
import kr.securityadvisor.assignment.dto.AssignmentResponse;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배정 API 중 담당자 본인 조회. 신청별 배정 조회·재배정은 신청 API(/api/security-requests/{id}/assignments)에 있다. */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** 내게 배정된 활성 배정(요청함 "내게 배정된 신청"). */
    @GetMapping("/mine")
    public List<AssignmentResponse> mine(@CurrentMember MemberPrincipal member) {
        return assignmentService.listMine(member);
    }
}
