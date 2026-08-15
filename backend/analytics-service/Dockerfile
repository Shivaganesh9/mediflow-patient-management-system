FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/analytics-service-*-exec.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
