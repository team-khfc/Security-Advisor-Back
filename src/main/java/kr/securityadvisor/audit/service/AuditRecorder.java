package kr.securityadvisor.audit.service;

import java.time.Clock;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditEvent;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.repository.AuditEventRepository;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.common.web.RequestIdHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 이력 기록기 — 다른 컨텍스트가 쓰는 공개 서비스. 호출한 업무 트랜잭션에 참여하므로(REQUIRED) 기록 실패는 업무 변경을
 * 롤백시키고, 업무가 롤백되면 기록도 남지 않는다(불변식 24). 트랜잭션 밖에서 부르면 자체 트랜잭션을 연다(로그인 실패 등).
 */
@Service
@RequiredArgsConstructor
public class AuditRecorder {

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    /** 로그인한 사용자의 행위를 기록한다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(
            MemberPrincipal actor,
            AuditTargetType targetType,
            String targetId,
            Long targetRevision,
            AuditAction action,
            String reason,
            String summary) {
        record(actor.username(), actor.role(), targetType, targetId, targetRevision, action, reason, summary);
    }

    /** 행위자 정보를 직접 넘기는 기록(로그인 전 단계·시스템 행위). actor 는 null 일 수 있다. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(
            String actorUsername,
            MemberRole actorRole,
            AuditTargetType targetType,
            String targetId,
            Long targetRevision,
            AuditAction action,
            String reason,
            String summary) {
        auditEventRepository.save(AuditEvent.of(
                clock.instant(),
                actorUsername,
                actorRole,
                targetType,
                targetId,
                targetRevision,
                action,
                reason,
                summary,
                RequestIdHolder.current()));
    }
}
