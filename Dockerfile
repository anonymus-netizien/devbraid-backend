# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies first (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Runtime Stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy compiled jar from build stage
COPY --from=build /app/target/devbraid.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
