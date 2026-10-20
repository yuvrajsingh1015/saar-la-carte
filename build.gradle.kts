plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("io.gitlab.arturbosch.detekt").version("1.23.8")
    jacoco
    application
}

val detektVersion = "1.23.8"

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin")
        resources.srcDir("src/main/resources")
    }
    test {
        kotlin.srcDir("src/test/kotlin")
        resources.srcDir("src/test/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

repositories {
    mavenCentral()
}

dependencies {
    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Detekt
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:$detektVersion")

    // CLI
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    // JSON
    implementation("org.json:json:20250517")
    implementation("com.github.erosb:json-sKema:0.23.0")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")

}

tasks.register("detectSuppressions") {
    val sources = listOf(
        sourceSets.main.get().kotlin,
        sourceSets.test.get().kotlin
    )
    sources.forEach { sourceSet ->
        sourceSet.forEach { file ->
            val regex = Regex("@.*suppress(.*)")
            file.forEachLine { line ->
                if (regex.containsMatchIn(line.lowercase())) {
                    throw GradleException("Suppressions of analysis tools detected in the following file: " + file.path)
                }
            }
        }
    }
}

tasks.withType<Copy> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

kotlin {
    jvmToolchain(21)
}

detekt {
    toolVersion = detektVersion
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

tasks.build {
    dependsOn(tasks["detectSuppressions"])
    dependsOn(tasks.javadoc)
    dependsOn(tasks.detektMain)
    dependsOn(tasks.detektTest)
}

tasks.jar {
    archiveFileName.set("saar-la-carte.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to "saarlacarte.MainKt",
            "Entry-Point" to "saarlacarte.MainKt",
            "Implementation-Title" to "Saar la Carte",
            "Application-Name" to "Saar la Carte",
            "Implementation-Version" to project.version,
        )
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

}

// Configure the main class for the application plugin
application {
    mainClass.set("saarlacarte.MainKt")
}

tasks.register<JavaExec>("runScenario") {
    description = "Runs the simulation with the parameters in gradle.properties"
    group = "application"
    dependsOn(tasks.jar)
    classpath(tasks.jar.get().archiveFile)
    mainClass.set("saarlacarte.MainKt")
    args = listOf("--food", properties["FOOD"].toString(),
        "--restaurants", properties["RESTAURANTS"].toString(),
        "--scenario", properties["SCENARIO"].toString(),
        "--maxTicks", properties["MAX_TICKS"].toString(),
        "--logLevel", properties["LOG_LEVEL"].toString(),
        "--out", properties["OUT"].toString()
    )
}
