FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

ENV JAVA_OPTS=""

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
