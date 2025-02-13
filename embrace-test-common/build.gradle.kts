plugins {
    kotlin("jvm")
    alias(libs.plugins.google.ksp)
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
    androidModule.set(false)
}

dependencies {
    implementation(libs.mockwebserver)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)
}
