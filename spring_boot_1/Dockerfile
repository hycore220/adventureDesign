# =============================================================================
#  save-it Spring 백엔드 — Fly.io 배포용 Dockerfile (멀티 스테이지)
#
#  Stage 1 (builder): Gradle 9 + JDK 17 로 fat jar 빌드
#  Stage 2 (runtime): JRE 17 만 들어있는 슬림 이미지에 jar 복사
#
#  - Gradle wrapper 가 외부망에 9.4.1 zip 을 받으러 가므로 빌드 단계엔 인터넷 필요
#  - 런타임 이미지는 약 200MB, 콜드 부팅 ~5초 수준
# =============================================================================

# ---- Stage 1: build ----
FROM gradle:9.0.0-jdk17 AS builder

WORKDIR /workspace

# 의존성 캐시 활용을 위해 빌드 스크립트만 먼저 복사
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ gradle/

# 의존성 미리 받기 (소스가 바뀌어도 이 레이어 캐시 재사용 가능)
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사
COPY src/ src/

# bootJar 만 빌드 (test 는 베타용이라 스킵)
RUN gradle --no-daemon bootJar -x test

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# HEALTHCHECK 와 디버깅용 curl. JRE 이미지엔 기본 포함 아니라 명시 설치.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 비루트 사용자로 실행 — 컨테이너 보안 모범사례
RUN groupadd -r spring && useradd -r -g spring spring

# Stage 1 에서 만든 jar 복사 (버전 번호와 무관하게 매칭)
COPY --from=builder /workspace/build/libs/*.jar app.jar

RUN chown spring:spring app.jar
USER spring

# Spring Boot 기본 포트
EXPOSE 8080

# JVM 컨테이너 메모리 자동 인식 + UTF-8 + 타임존
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"

# Fly health check 가 /health 를 두드림 — application.properties + SecurityConfig 와 일관
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
