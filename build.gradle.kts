import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "dev.adk"
version = "0.1.0-SNAPSHOT"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_22)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("dev.adk.kotlin.cli.AdkCliMainKt")
}
