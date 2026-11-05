import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("embrace-prod-jvm-conventions")
    alias(libs.plugins.wire)
}

description = "Embrace Android SDK: Storage"

tasks.withType<Detekt>().configureEach {
    exclude("**/generated/source/wire/**")
}

wire {
    kotlin {
        rpcRole = "none"
        javaInterop = false
        emitDeclaredOptions = false
        emitAppliedOptions = false
    }
}

dependencies {
    api(project(":embrace-android-infra"))
    api(libs.okio)
    implementation(libs.wire.runtime)

    testImplementation(project(":embrace-test-common"))
}
