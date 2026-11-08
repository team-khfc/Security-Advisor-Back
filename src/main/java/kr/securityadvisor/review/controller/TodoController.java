package kr.securityadvisor.review.controller;

import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.review.dto.TodoSummary;
import kr.securityadvisor.review.service.TodoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인한 사람의 "처리할 일" 집계(F-17 배지). 읽기 전용 자원. */
@RestController
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping("/api/todo-summary")
    public TodoSummary summary(@CurrentMember MemberPrincipal member) {
        return todoService.summarize(member);
    }
}
