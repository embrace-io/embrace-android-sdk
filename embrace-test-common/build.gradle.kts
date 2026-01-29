plugins {
    id("embrace-jvm-conventions")
    alias(libs.plugins.google.ksp)
}

dependencies {
    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.mockwebserver)
    implementation(libs.moshi)
    implementation(libs.robolectric)
    ksp(libs.moshi.kotlin.codegen)
}
