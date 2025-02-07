import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "io.embrace.internal"
version = "1.0.0"

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
}

// NOTE: when updating any of these keep in sync with the version catalog
dependencies {
    implementation(gradleApi())

    // Version of Kotlin used at build time
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.17.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1")
}

gradlePlugin {
    plugins {
        create("embracePlugin") {
            id = "io.embrace.internal.build-logic"
            implementationClass = "io.embrace.internal.BuildPlugin"
        }
    }
}

// ensure the Kotlin + Java compilers both use the same language level.
project.tasks.withType(JavaCompile::class.java).configureEach {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

// ensure the Kotlin + Java compilers both use the same language level.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}
