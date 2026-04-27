FROM eclipse-temurin:17-jdk-jammy

LABEL author="Tanaka Musungare"

WORKDIR /app

COPY target/TestAI-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8083

ENV SPRING_PROFILES_ACTIVE=default

ENTRYPOINT ["java", "-Dserver.port=8083", "-jar", "app.jar"]
