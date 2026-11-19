plugins {
    kotlin("jvm")
    `java-library`
}

kotlin { jvmToolchain(22) }

dependencies {
    api(project(":core"))
    api(project(":repository"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("com.github.librepdf:openpdf:3.0.5")
    implementation(files(rootProject.file("libs/ShippingApiProvider-1.1.0-beta.6.jar")))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("com.github.librepdf:openpdf:3.0.5")
    testImplementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
}

tasks.register<Test>("fedexSandboxShipmentTest") {
    description = "Creates one real FedEx sandbox shipment and verifies its returned PDF label."
    group = "verification"
    dependsOn(tasks.named("testClasses"))
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/FedExSandboxShipmentIntegrationTest.class")
    environment("FEDEX_MODE", "SANDBOX")

    val envFile = rootProject.file(".env")
    if (envFile.isFile) {
        val localSettings = envFile.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else {
                    val key = line.substring(0, separator).trim()
                    val value = line.substring(separator + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    key to value
                }
            }.toMap()
        listOf("FEDEX_TEST_PT_CLIENT_ID", "FEDEX_TEST_PT_CLIENT_SECRET", "FEDEX_TEST_PT_ACCOUNT_NUMBER")
            .forEach { key -> localSettings[key]?.let { environment(key, it) } }
    }
}
