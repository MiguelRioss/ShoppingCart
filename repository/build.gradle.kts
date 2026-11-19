plugins {
    kotlin("jvm")
    `java-library`
}

kotlin { jvmToolchain(22) }

dependencies {
    api(project(":core"))
    implementation("org.postgresql:postgresql:42.7.12")
    implementation("com.zaxxer:HikariCP:7.1.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
