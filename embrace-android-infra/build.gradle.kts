plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

description = "Embrace Android SDK: Infra"

dependencies {
    implementation(libs.androidx.annotation)
}
