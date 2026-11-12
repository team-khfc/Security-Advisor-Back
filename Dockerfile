# 백엔드 이미지. 1단계에서 jar 를 빌드하고, 2단계에서는 JRE 만 담아 가볍게 실행한다.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# 빌드 설정만 먼저 복사해 의존성을 받아 둔다. 소스만 바뀌면 이 단계는 캐시를 재사용한다.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --no-daemon -q > /dev/null

COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-kor tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /data/storage && chown app:app /data/storage
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
