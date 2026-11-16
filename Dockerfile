# ---- Build stage: compile the jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy pom first so dependency download is cached when only code changes
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- Run stage: small JRE image ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/job-portal-0.1.0.jar app.jar

# Render free tier has 512 MB RAM: cap heap and use the low-overhead serial GC
ENV JAVA_OPTS="-Xmx300m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
