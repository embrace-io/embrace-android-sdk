plugins {
    id("embrace-public-api-conventions")
}

description = "Embrace Android SDK: API"

android {
    namespace = "io.embrace.android.embracesdk.api"
}

dependencies {
    compileOnly(libs.opentelemetry.kotlin.api)
    implementation(libs.androidx.annotation)
    lintChecks(project(":embrace-lint"))
}
