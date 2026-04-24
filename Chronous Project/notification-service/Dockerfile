FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/notification-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java","-jar","/app/app.jar"]
