FROM eclipse-temurin:21-jre
RUN set -eux; \
    if command -v groupadd >/dev/null 2>&1; then groupadd --system --gid 10001 mediflow; else addgroup --system --gid 10001 mediflow; fi; \
    if command -v useradd >/dev/null 2>&1; then useradd --system --uid 10001 --gid mediflow --home-dir /app --no-create-home --shell /usr/sbin/nologin mediflow; else adduser --system --uid 10001 --ingroup mediflow --home /app --shell /sbin/nologin mediflow; fi
WORKDIR /app
COPY --chown=10001:10001 target/auth-service-*-exec.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
