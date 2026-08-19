FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/medical-record-service-*-exec.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
