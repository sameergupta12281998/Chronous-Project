FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/execution-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java","-jar","/app/app.jar"]
