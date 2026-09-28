# Build stage 1: the web app (src/frontend) into src/main/resources/static.
FROM node:22 AS web
WORKDIR /app/src/frontend
COPY src/frontend/package*.json ./
RUN npm ci
COPY src/frontend/ ./
# vite build writes straight into ../main/resources/static
RUN npm run build

# Build stage 2: the Spring Boot jar, including the web app.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src/main ./src/main
COPY --from=web /app/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# Run stage: small JRE image, non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
# Keep memory modest on small VPS plans.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
