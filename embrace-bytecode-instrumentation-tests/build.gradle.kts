plugins {
    id("com.android.library")
    kotlin("android")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
    jvmTarget.set(JavaVersion.VERSION_11)
}

android {
    namespace = "io.embrace.android.gradle.test.fixtures"
    compileSdk = 34
    defaultConfig.minSdk = 21

    buildTypes {
        release {
        }
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.appcompat)

    testImplementation(libs.agp.api)
    testImplementation(libs.junit)
    testImplementation(project(":embrace-gradle-plugin"))
    testImplementation(libs.asm.util)
    testImplementation(libs.gradle.test.kit)
}
