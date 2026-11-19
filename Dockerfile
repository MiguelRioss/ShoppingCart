FROM eclipse-temurin:22-jdk AS build

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY core/build.gradle.kts ./core/
COPY repository/build.gradle.kts ./repository/
COPY services/build.gradle.kts ./services/
COPY http/build.gradle.kts ./http/
COPY server/build.gradle.kts ./server/
COPY gradle ./gradle
COPY libs ./libs

RUN chmod +x ./gradlew

COPY core ./core
COPY repository ./repository
COPY services ./services
COPY http ./http
COPY server ./server

RUN ./gradlew :server:compileKotlin --no-daemon --console=plain --stacktrace
RUN ./gradlew :server:installDist --no-daemon --console=plain --stacktrace

FROM eclipse-temurin:22-jre

WORKDIR /app

ENV PORT=8080

COPY --from=build /app/server/build/install/ShoppingCart ./

EXPOSE 8080

CMD ["./bin/ShoppingCart"]
