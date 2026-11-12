package kr.securityadvisor.review.service;

import java.util.List;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.review.dto.TodoItem;

/** 다른 업무 영역이 현재 권한에 맞는 처리할 일을 제공한다. */
public interface TodoContributor {
    List<TodoItem> items(MemberPrincipal member);
}
