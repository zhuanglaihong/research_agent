FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline -DskipTests
COPY src src
RUN ./mvnw -B -ntp package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p /app/data/workspaces && chown -R 10001:10001 /app
COPY --from=build /build/target/research_agent-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8123
ENTRYPOINT ["java", "-jar", "app.jar"]
