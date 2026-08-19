FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/doctor-service-*-exec.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
