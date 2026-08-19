FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/api-gateway-*-exec.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Kolkata", "-jar", "/app/app.jar"]
