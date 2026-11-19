plugins {
    kotlin("jvm") version "2.0.21" apply false
    base
}

allprojects {
    group = "shoppingcart"
    version = "0.1"
    repositories { mavenCentral() }
}

subprojects {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform {
            if (providers.gradleProperty("runLiveEmailTest").orNull != "true") excludeTags("live")
        }
        outputs.upToDateWhen { false }
        testLogging { showStandardStreams = true }
    }
}

tasks.register<Exec>("emailApiCheck") {
    description = "Checks ShoppingCart's calls to the deployed EMAIL_SENDER V2 API."
    group = "verification"
    val command = mutableListOf("node", "scripts/check-email-api.mjs", "--route", providers.gradleProperty("route").getOrElse("all"))
    listOf("requestFile" to "request-file", "baseUrl" to "base-url", "reference" to "reference").forEach { (property, option) ->
        providers.gradleProperty(property).orNull?.let { command.addAll(listOf("--$option", it)) }
    }
    commandLine(command)
}
