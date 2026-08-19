FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/notification-service-*-exec.jar app.jar
EXPOSE 8089
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
