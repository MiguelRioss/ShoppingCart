plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(22) }

application {
    mainClass.set("MainKt")
    applicationName = "ShoppingCart"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core"))
    implementation(project(":repository"))
    implementation(project(":services"))
    implementation(project(":http"))
    implementation(files(rootProject.file("libs/ShippingApiProvider-1.1.0-beta.6.jar")))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}
