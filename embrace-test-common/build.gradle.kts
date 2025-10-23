plugins {
    kotlin("jvm")
    alias(libs.plugins.google.ksp)
    id("io.embrace.internal.build-logic")
}

dependencies {
    implementation(platform(libs.okhttp.bom))
    implementation(libs.mockwebserver)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
}
