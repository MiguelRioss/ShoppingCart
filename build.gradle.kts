plugins {
    kotlin("jvm") version "2.0.21"
    application
}

version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("org.postgresql:postgresql:42.7.4")
    testImplementation(kotlin("test"))
    testImplementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("org.apache.poi:poi:5.4.1")
    testImplementation("org.apache.poi:poi-ooxml:5.4.1")
}

kotlin {
    jvmToolchain(22)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

application {
    mainClass.set("MainKt")
}

sourceSets {
    main {
        kotlin.srcDir("src")
        kotlin.srcDir("fedex/src")
    }
    test {
        kotlin.srcDir("test")
        kotlin.srcDir("fedex/test")
    }
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    testLogging {
        showStandardStreams = true
    }
}
