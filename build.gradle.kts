// 백엔드 빌드 설정.
// 버전을 올릴 때는 docs/adr/0001-tech-stack.md 의 버전 표도 함께 고친다.
plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.4"
}

group = "kr.securityadvisor"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val springdocVersion = "3.1.1"
val archunitVersion = "1.4.2"
// Lombok 은 허용 목록(@Getter·@RequiredArgsConstructor·@NoArgsConstructor(PROTECTED)·@Slf4j)만 쓴다(AGENTS.md 5장).
// 금지 애너테이션은 루트의 lombok.config 가 컴파일 오류로 막는다. Java 25 지원 버전으로 고정.
val lombokVersion = "1.18.42"

dependencies {
    // 웹(MVC), 검증, 운영 상태 확인
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 인증·권한(시연용 세션 로그인, 역할·기관 격리 검사)
    implementation("org.springframework.boot:spring-boot-starter-security")

    // 데이터베이스: JPA + Flyway(스키마 버전 관리) + PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // API 문서(Swagger UI): http://localhost:8080/swagger-ui.html
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // Lombok(컴파일 시점 전용 — 런타임 의존 없음)
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    testCompileOnly("org.projectlombok:lombok:$lombokVersion")
    testAnnotationProcessor("org.projectlombok:lombok:$lombokVersion")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // 테스트: 실제 PostgreSQL 컨테이너(Testcontainers)로 DB 테스트, ArchUnit 으로 패키지 규칙 검사
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // 파라미터 이름을 런타임에 유지 (@PathVariable 등에서 이름 생략 가능)
    options.compilerArgs.add("-parameters")
}

// 다른 환경(오프라인 VM 등)에서 같은 소스 트리를 빌드할 때 PC 쪽 build/ 결과를 덮어쓰지 않도록 출력 폴더를 바꿀 수 있다.
// 예: gradlew test -PbuildDirOverride=/home/me/build-out
project.findProperty("buildDirOverride")?.toString()?.takeIf { it.isNotBlank() }?.let {
    layout.buildDirectory.set(file(it))
}

tasks.withType<Test> {
    useJUnitPlatform {
        // Docker 가 없는 환경(오프라인 VM 등)에서 단위 테스트만 돌릴 때: gradlew test -PexcludeTags=docker
        val excluded = project.findProperty("excludeTags")?.toString()
        if (!excluded.isNullOrBlank()) {
            excludeTags(*excluded.split(',').map { it.trim() }.toTypedArray())
        }
    }
}

// 실행 가능한 jar(bootJar)만 만든다. -plain.jar 는 만들지 않는다.
tasks.named<Jar>("jar") {
    enabled = false
}

// 코드 포맷 통일. 직접 맞추지 말고 ./gradlew spotlessApply 로 자동 정리한다.
// ./gradlew check 에 spotlessCheck 가 포함되어, 포맷이 다르면 빌드가 실패한다.
spotless {
    // Windows·Linux 어디서 돌려도 같은 결과가 나오도록 줄 끝을 LF 로 고정(.gitattributes 와 동일)
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    java {
        target("src/**/*.java")
        palantirJavaFormat()
        removeUnusedImports()
        importOrder()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
