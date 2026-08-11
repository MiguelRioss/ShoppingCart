FROM eclipse-temurin:22-jdk AS build

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:22-jre

WORKDIR /app

ENV PORT=8080

COPY --from=build /app/build/install/ShoppingCart ./

EXPOSE 8080

CMD ["./bin/ShoppingCart"]
