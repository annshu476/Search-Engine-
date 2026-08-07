FROM maven:3.9.14-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY . .
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring
COPY --from=build /workspace/target/url-frontier-0.0.1-SNAPSHOT.jar app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
