plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

dependencies {
    api(project(":embrace-android-instrumentation-schema"))
    api(project(":embrace-android-infra"))
    api(project(":embrace-android-config"))
}
