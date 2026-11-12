package kr.securityadvisor.request.service;

import java.time.LocalDate;

/** 확정 이후 운영 업무가 신청 배정과 공통 예약 알림에 참여하는 공개 경계. */
public interface OperationalWork {
    boolean hasOngoingWork(Long requestId);

    void remind(LocalDate asOf, Long onlyRequestId);
}
