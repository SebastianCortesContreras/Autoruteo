
# Etapa 1: Compilar la aplicación con Maven
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Crear la imagen final ligera
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/routing-automation-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080

# Explicit heap sizing keeps memory usage predictable on constrained Railway
# instances. Without -Xmx, the JVM defaults to 25% of container memory, which
# combined with unbounded Timefold solving was causing OOM restarts.
# UseSerialGC further reduces memory overhead (no extra GC bookkeeping
# structures), which matters more than throughput for this low-heap workload.
ENV JAVA_OPTS="-Xms512m -Xmx768m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
