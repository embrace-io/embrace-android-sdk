plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

dependencies {
    api(project(":embrace-android-instrumentation-schema"))
    api(project(":embrace-android-infra"))
    api(project(":embrace-android-config"))
}
