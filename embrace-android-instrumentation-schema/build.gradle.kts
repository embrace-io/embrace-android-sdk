plugins {
    id("embrace-prod-jvm-conventions")
}

dependencies {
    implementation(project(":embrace-android-error-handling"))
    implementation(project(":embrace-android-semconv"))
    implementation(libs.opentelemetry.kotlin.semconv)
}
