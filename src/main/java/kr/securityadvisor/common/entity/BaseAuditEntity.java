package kr.securityadvisor.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

/**
 * 생성·수정 시각에 더해 누가 만들고 고쳤는지(username)까지 기록하는 엔티티의 부모.
 *
 * <p>요청·답변·확인 이력을 "담당자·시각" 과 함께 보존해야 하는 업무 엔티티(신청·협의·보완 등)가 상속한다.
 * 값은 {@link kr.securityadvisor.common.config.JpaConfig} 의 AuditorAware 가 로그인 사용자에서 채운다.
 */
@MappedSuperclass
public abstract class BaseAuditEntity extends BaseTimeEntity {

    @CreatedBy
    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
