FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon || return 0

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre AS run
WORKDIR /app
COPY --from=build /app/build/libs/*.jar chalkak.jar

ENTRYPOINT ["java", "-jar", "chalkak.jar"]
