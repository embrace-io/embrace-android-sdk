plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

dependencies {
    implementation(project(":embrace-android-instrumentation-api"))
}
