FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/medical-record-service-*-exec.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
