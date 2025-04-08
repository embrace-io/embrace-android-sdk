plugins {
    id("com.android.library")
    id("kotlin-android")
    id("io.embrace.internal.build-logic")
}

embrace {
    productionModule.set(false)
}

android {
    namespace = "io.embrace.android.embracesdk.test.fakes"
}

dependencies {
    compileOnly(project(":embrace-android-core"))
    compileOnly(project(":embrace-android-infra"))
    compileOnly(project(":embrace-android-sdk"))
    compileOnly(project(":embrace-android-payload"))
    compileOnly(project(":embrace-android-features"))
    compileOnly(project(":embrace-android-delivery"))
    compileOnly(project(":embrace-internal-api"))

    compileOnly(platform(libs.opentelemetry.bom))
    compileOnly(libs.opentelemetry.api)
    compileOnly(libs.opentelemetry.sdk)
    compileOnly(libs.opentelemetry.semconv)
    compileOnly(libs.opentelemetry.semconv.incubating)

    implementation(libs.junit)
    implementation(libs.okhttp)
    implementation(libs.robolectric)
    implementation(libs.lifecycle.runtime)

    implementation(libs.opentelemetry.kotlin.api)
    implementation(libs.opentelemetry.kotlin.api.ext)
    implementation(libs.opentelemetry.kotlin.compat)
}
