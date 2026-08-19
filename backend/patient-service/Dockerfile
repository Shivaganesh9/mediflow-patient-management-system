FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/patient-service-*-exec.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
