plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

dependencies {
    implementation(project(":embrace-test-common"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
}
