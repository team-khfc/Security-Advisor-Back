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

    /** 현재 내 역할·배정으로 처리 가능한 업무를 페이지로 조회한다. */
    @GetMapping("/api/todos")
    public kr.securityadvisor.common.dto.PageResponse<kr.securityadvisor.review.dto.TodoItem> list(
            @CurrentMember MemberPrincipal member,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        return todoService.list(member, page, size);
    }

    @GetMapping("/api/todo-summary")
    public TodoSummary summary(@CurrentMember MemberPrincipal member) {
        return todoService.summarize(member);
    }
}
