import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("embrace-prod-jvm-conventions")
    alias(libs.plugins.wire)
}

description = "Embrace Android SDK: Session Persistence"

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
    api(libs.wire.runtime)

    implementation(project(":embrace-android-payload"))
    implementation(project(":embrace-android-infra"))
    implementation(project(":embrace-android-telemetry-persistence"))

    testImplementation(project(":embrace-test-common"))
}
