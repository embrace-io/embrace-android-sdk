plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
}

dependencies {
    implementation(libs.opentelemetry.kotlin.semconv)
}
