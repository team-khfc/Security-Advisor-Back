package kr.securityadvisor.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * AGENTS.md 의 "백엔드 패키지 규칙"과 FUNCTIONAL_SPEC 1장(바운디드 컨텍스트·domain 계층 독립)을 자동으로 검사한다.
 * 규칙을 바꾸면 AGENTS.md 와 이 파일을 함께 고친다.
 *
 * <p>실패 메시지(because)는 사람과 AI 가 바로 고칠 수 있도록 무엇을 해야 하는지 적는다.
 */
@AnalyzeClasses(packages = "kr.securityadvisor", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT_PACKAGE = "kr.securityadvisor.";
    private static final String COMMON_CONTEXT = "common";

    @ArchTest
    static final ArchRule controllerDoesNotUseRepository = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..repository..")
            .because("컨트롤러는 서비스만 호출한다. 조회 로직은 service 패키지로 옮길 것");

    @ArchTest
    static final ArchRule controllerDoesNotExposeEntity = noClasses()
            .that()
            .resideInAPackage("..controller..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..domain..")
            .because("API 는 엔티티 대신 dto 패키지의 record 로 응답한다");

    @ArchTest
    static final ArchRule serviceDoesNotDependOnController = noClasses()
            .that()
            .resideInAPackage("..service..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..controller..")
            .because("의존 방향은 controller → service → repository/domain 한 방향이다");

    @ArchTest
    static final ArchRule domainIsIndependent = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service..", "..dto..", "..repository..")
            .because("엔티티(domain)는 다른 계층을 알지 못한다");

    @ArchTest
    static final ArchRule domainDoesNotUseWebOrAiFrameworks = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.ai..",
                    "org.springframework.security..",
                    "org.springframework.stereotype..",
                    "jakarta.servlet..")
            .because("domain 계층은 Spring Web·Spring AI·Security 를 모른다(FUNCTIONAL_SPEC 1장). "
                    + "불변식은 순수 Java 도메인 메서드·예외로 구현하고 프레임워크 없는 단위 테스트를 작성할 것");

    @ArchTest
    static final ArchRule domainReferencesOtherContextsOnlyById = classes()
            .that()
            .resideInAPackage("..domain..")
            .should(onlyDependOnDomainOfOwnContext())
            .because("컨텍스트 간 참조는 ID 와 공개 서비스 인터페이스로만 한다(FUNCTIONAL_SPEC 1장). "
                    + "다른 컨텍스트의 엔티티 대신 requestId 같은 식별자 필드를 둘 것");

    @ArchTest
    static final ArchRule featuresHaveNoCycles = slices().matching("kr.securityadvisor.(*)..")
            .should()
            .beFreeOfCycles()
            .because("기능 패키지끼리 서로를 참조하면 분리·수정이 어려워진다. 공통 코드는 common 으로 옮길 것");

    /** 다른 바운디드 컨텍스트의 domain 클래스를 직접 참조하면 위반이다. common 의 공유 값 객체는 허용한다. */
    private static ArchCondition<JavaClass> onlyDependOnDomainOfOwnContext() {
        return new ArchCondition<>("자기 컨텍스트(또는 common)의 domain 만 참조한다") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownContext = contextOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    if (!target.getPackageName().contains(".domain")) {
                        continue;
                    }
                    String targetContext = contextOf(target);
                    if (targetContext == null
                            || targetContext.equals(ownContext)
                            || targetContext.equals(COMMON_CONTEXT)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                }
            }
        };
    }

    /** kr.securityadvisor.<컨텍스트>.… 에서 컨텍스트 이름을 꺼낸다. 프로젝트 밖 클래스는 null. */
    private static String contextOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(ROOT_PACKAGE)) {
            return null;
        }
        String rest = packageName.substring(ROOT_PACKAGE.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
