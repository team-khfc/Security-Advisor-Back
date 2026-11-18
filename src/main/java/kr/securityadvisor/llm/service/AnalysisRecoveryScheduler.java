package kr.securityadvisor.llm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 메모리 이벤트를 놓쳐도 DB에 접수된 작업은 다시 실행한다. */
@Component
@RequiredArgsConstructor
public class AnalysisRecoveryScheduler {
    private final DocumentAnalysisService analysis;

    @Scheduled(fixedDelayString = "${securityadvisor.analysis.recovery-delay-ms:30000}", initialDelay = 30000)
    public void recover() {
        for (Long id : analysis.recoverable()) analysis.process(new DocumentAnalysisService.AnalysisQueued(id));
    }
}
