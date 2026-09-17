FROM eclipse-temurin:22-jdk AS build

WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

RUN chmod +x ./gradlew

COPY src ./src

# Make Kotlin compilation errors visible in Render
RUN ./gradlew compileKotlin --no-daemon --console=plain --stacktrace

# Build the distribution
RUN ./gradlew installDist --no-daemon --console=plain --stacktrace

FROM eclipse-temurin:22-jre

WORKDIR /app

ENV PORT=8080

COPY --from=build /app/build/install/ShoppingCart ./

EXPOSE 8080

CMD ["./bin/ShoppingCart"]