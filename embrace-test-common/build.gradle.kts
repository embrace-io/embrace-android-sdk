import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

project.tasks.withType(KotlinCompile::class.java).configureEach {
    kotlinOptions {
        apiVersion = "1.7"
        languageVersion = "1.7"
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}