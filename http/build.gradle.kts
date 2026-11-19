plugins {
    kotlin("jvm")
    `java-library`
}

kotlin { jvmToolchain(22) }

dependencies {
    api(project(":core"))
    api(project(":repository"))
    api(project(":services"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
