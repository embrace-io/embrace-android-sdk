plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

description = "Embrace Android SDK: Infra"

dependencies {
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
}
