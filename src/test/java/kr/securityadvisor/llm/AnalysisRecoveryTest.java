package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import kr.securityadvisor.llm.domain.*;
import org.junit.jupiter.api.Test;

class AnalysisRecoveryTest {
    @Test
    void 중단_실행은_임대_만료후_세번까지만_복구한다() {
        var job = AnalysisJob.queue(1L, 2L, "synthetic");
        var time = Instant.EPOCH;
        for (int attempt = 1; attempt <= 3; attempt++) {
            job.start(time);
            assertThat(job.getAttempts()).isEqualTo(attempt);
            job.recover(time.plusSeconds(899));
            assertThat(job.getStatus()).isEqualTo(AnalysisStatus.RUNNING);
            job.recover(time.plusSeconds(901));
            assertThat(job.getStatus()).isEqualTo(attempt == 3 ? AnalysisStatus.FAILED : AnalysisStatus.QUEUED);
            time = time.plusSeconds(1000);
        }
    }

    @Test
    void 완료된_작업은_복구하지_않는다() {
        var job = AnalysisJob.queue(1L, 2L, "synthetic");
        job.start(Instant.EPOCH);
        job.finish("합성 요약", "fake/test", Instant.EPOCH);
        job.recover(Instant.EPOCH.plusSeconds(10000));
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.NEEDS_REVIEW);
    }
}
