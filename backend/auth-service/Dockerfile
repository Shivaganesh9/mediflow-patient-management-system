FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/auth-service-*-exec.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
