package kr.securityadvisor.notification.service;

import kr.securityadvisor.notification.domain.Notification;

/**
 * 화면 밖 전달 채널(메일·메신저, F-17 P3). 화면 안 알림이 저장된 뒤 호출되며, 실패해도 업무·화면 알림에 영향을 주지 않는다.
 * 본문에는 제목·링크만 넣고 값(IP·서버명 등)은 넣지 않는다 — 제목 자체가 그렇게 만들어진다.
 */
public interface NotificationChannel {

    /** 채널 이름(화면 표시·설정 조회용). */
    String name();

    /** 설정이 있어 실제로 보낼 수 있는지. 꺼져 있으면 호출되지 않는다. */
    boolean enabled();

    /** 한 건 전달. 수신자가 이 채널 주소를 갖고 있지 않으면 조용히 건너뛴다. 예외는 호출자가 로그만 남긴다. */
    void deliver(Notification notification);
}
