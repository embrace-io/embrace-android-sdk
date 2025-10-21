plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

embrace {
}

dependencies {
    implementation(project(":embrace-android-payload"))
    testImplementation(project(":embrace-android-config-fakes"))
}
