# Image du backend : compilation Maven puis JRE seul. Multi-architecture (x86 et ARM, ex. Oracle Ampere).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER app
ENV TZ=Europe/Paris \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Europe/Paris"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
