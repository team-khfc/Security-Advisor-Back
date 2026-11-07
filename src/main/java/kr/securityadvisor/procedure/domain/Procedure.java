package kr.securityadvisor.procedure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.securityadvisor.common.entity.BaseTimeEntity;

/**
 * 보안 절차(예: 보안성검토, 방화벽 허용 신청). 직원이 신청서를 작성할 때 고르는 절차의 종류다.
 *
 * <p>예제 기능이다. 새 기능을 만들 때 이 패키지(kr.securityadvisor.procedure)의 구조를 그대로 따른다.
 */
@Entity
@Table(name = "procedures")
public class Procedure extends BaseTimeEntity {

    /** 기본 키 컬럼은 테이블명을 붙인 {@code procedure_id} 다(팀 규칙: 같은 이름의 컬럼은 같은 의미·형식, 맨 {@code id} 금지). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "procedure_id")
    private Long id;

    /** 절차를 구분하는 고정 코드. 화면·API 에서 id 대신 사용한다. 예: SECURITY_REVIEW */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    /** false 이면 목록에 노출하지 않는다. 데이터는 지우지 않고 비활성화한다. */
    @Column(nullable = false)
    private boolean active;

    /** JPA 전용 기본 생성자. 코드에서 직접 호출하지 않는다. */
    protected Procedure() {}

    public Procedure(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    /** 절차를 목록에서 숨긴다. 이미 작성된 신청서가 참조할 수 있으므로 삭제 대신 사용한다. */
    public void deactivate() {
        this.active = false;
    }
}
