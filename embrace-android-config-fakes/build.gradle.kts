plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

embrace {
}

dependencies {
    implementation(project(":embrace-android-config"))
    implementation(project(":embrace-android-payload"))
}
