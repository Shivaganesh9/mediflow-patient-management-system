FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/appointment-service-*-exec.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
