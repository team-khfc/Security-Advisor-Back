package kr.securityadvisor.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러 메서드 파라미터에 붙여 로그인한 사용자({@link MemberPrincipal})를 주입받는다.
 *
 * <pre>{@code
 * @GetMapping
 * public List<...> list(@CurrentMember MemberPrincipal member) { ... }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal(errorOnInvalidType = true)
public @interface CurrentMember {}
