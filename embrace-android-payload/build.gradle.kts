plugins {
    id("com.google.devtools.ksp")
    id("embrace-prod-jvm-conventions")
}

description = "Embrace Android SDK: Payload"

dependencies {
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
    testImplementation(libs.junit)
}
