plugins {
    kotlin("jvm")
    `java-library`
}

kotlin { jvmToolchain(22) }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    api(files(rootProject.file("libs/ShippingApiProvider-1.1.0-beta.6.jar")))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
