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

    // NOTE: when updating any of these keep in sync with buildSrc/src/main/kotlin/io/embrace/gradle/Versions.kt
    implementation("com.android.tools.build:gradle:7.4.2")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.21")
    implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.14.0")
}

// ensure the Kotlin + Java compilers both use the same language level.
project.tasks.withType(JavaCompile::class.java) {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}
