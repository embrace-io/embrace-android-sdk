plugins {
    kotlin("jvm")
    id("io.embrace.internal.build-logic")
    id("com.vanniktech.maven.publish")
}

dependencies {
    implementation(libs.opentelemetry.kotlin.semconv)
}
