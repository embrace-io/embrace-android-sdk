plugins {
    id("embrace-prod-jvm-conventions")
}

dependencies {
    implementation(libs.opentelemetry.kotlin.semconv)
}
