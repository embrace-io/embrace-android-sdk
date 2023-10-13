plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("internal-embrace-plugin") {
            id = "internal-embrace-plugin"
            implementationClass = "io.embrace.gradle.InternalEmbracePlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())

    // TODO: future - these versions must be kept in sync when updating buildscript deps.
    implementation("com.android.tools.build:gradle:7.3.0")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.21.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.32")
    implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.12.1")
}
