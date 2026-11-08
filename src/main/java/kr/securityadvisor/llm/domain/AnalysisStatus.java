package kr.securityadvisor.llm.domain;

/** 분석 작업 상태(F-02). 화면의 "선택됨·전송 중"은 클라이언트 상태이고 서버는 접수 이후만 가진다. */
public enum AnalysisStatus {
    QUEUED("분석 대기"),
    RUNNING("분석 중"),
    NEEDS_REVIEW("확인 필요"),
    FAILED("실패");

    private final String label;

    AnalysisStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
