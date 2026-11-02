FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package dependency:copy-dependencies

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/dependency ./lib
COPY --from=build /build/target/*.jar ./app.jar
ENTRYPOINT ["java", "-cp", "/app/app.jar:/app/lib/*", "pl.football.seeder.Main"]
