import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("embrace-android-conventions")
}

android {
    namespace = "io.embrace.android.gradle.test.fixtures"
    compileSdk = 36
    defaultConfig.minSdk = 21

    buildTypes {
        release {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            allWarningsAsErrors.set(false)
        }
    }
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.appcompat)

    testImplementation(project(":embrace-gradle-plugin"))
    testImplementation(libs.agp.api)
    testImplementation(libs.junit)
    testImplementation(libs.asm.util)
    testImplementation(libs.gradle.test.kit)
    testImplementation(libs.mockk)
}
