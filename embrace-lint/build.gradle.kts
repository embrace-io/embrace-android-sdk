import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-library")
    id("kotlin")
    id("com.android.lint")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
    androidLibrary.set(false)
    jvmTarget.set(JavaVersion.VERSION_11)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    compileOnly(libs.lint.api)
    testCompileOnly(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}
