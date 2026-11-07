// JDK 25가 없는 PC에서도 Gradle이 자동으로 JDK를 내려받도록 한다.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "securityadvisor-backend"
