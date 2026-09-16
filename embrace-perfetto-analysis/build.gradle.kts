import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("embrace-jvm-conventions")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.wire)
}

description = "Embrace Android SDK: Perfetto trace analysis"

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
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.wire.runtime)
}

tasks.register<JavaExec>("analyseTrace") {
    group = "verification"
    description = "Analyses a .perfetto-trace file."
    mainClass.set("io.embrace.android.embracesdk.internal.perfetto.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("analyseIterations") {
    group = "verification"
    description = "Aggregates statistics across every iteration trace of a macrobenchmark run."
    mainClass.set("io.embrace.android.embracesdk.internal.perfetto.IterationsMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("compareIterations") {
    group = "verification"
    description = "Compares two macrobenchmark runs. Writing the report is not implemented."
    mainClass.set("io.embrace.android.embracesdk.internal.perfetto.CompareMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}
