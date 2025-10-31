plugins {
    id("embrace-jvm-conventions")
}

dependencies {
    implementation(project(":embrace-test-common"))
    implementation(project(":embrace-android-delivery"))
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
}
