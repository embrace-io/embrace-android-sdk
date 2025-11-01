plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    testImplementation(project(":embrace-android-config-fakes"))
}
