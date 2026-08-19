FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/analytics-service-*-exec.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
