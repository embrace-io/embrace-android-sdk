plugins {
    id("embrace-jvm-conventions")
}

description = "Embrace Android SDK: Perfetto trace analysis"

tasks.register<JavaExec>("analyseTrace") {
    group = "verification"
    description = "Analyses a .perfetto-trace file."
    mainClass.set("io.embrace.android.embracesdk.internal.perfetto.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}
