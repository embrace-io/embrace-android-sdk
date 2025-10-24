import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

dependencies {
    implementation(gradleApi())
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.agp)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.binary.compatibility.validator)
    implementation(libs.vanniktech.maven.publish)
    implementation(libs.kotlin.metadata.jvm)
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
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
}

// ensure the Kotlin + Java compilers both use the same language level.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
