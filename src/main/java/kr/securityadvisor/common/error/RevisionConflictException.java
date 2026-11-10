package kr.securityadvisor.common.error;

/** 입력을 덮어쓰지 않고 최신 revision을 확인하도록 안내하는 충돌. */
public class RevisionConflictException extends ConflictException {
    private final long currentRevision;

    public RevisionConflictException(String message, long currentRevision) {
        super(message);
        this.currentRevision = currentRevision;
    }

    public long getCurrentRevision() {
        return currentRevision;
    }
}
