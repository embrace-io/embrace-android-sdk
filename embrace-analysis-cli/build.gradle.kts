// embrace-analysis-cli: the startup-analysis tools' executable. Run it through the checked-in wrapper `tools/startup`,
// which builds this module incrementally and then execs the installDist launcher, NOT through `./gradlew run`, which
// swallows stdin, turns exit codes into exceptions and breaks terminal detection.
plugins {
    id("embrace-jvm-conventions")
    application
}

description = "Embrace Android SDK: startup analysis tools"

application {
    applicationName = "startup-tools"
    mainClass.set("io.embrace.analysis.MainKt")
}

// The trace_processor_shell version the version catalog pins, as a resource the tools read to download the binary.
val traceProcessorVersion = tasks.register("traceProcessorVersion") {
    val version = libs.versions.perfettoTraceProcessor.get()
    val output = layout.buildDirectory.dir("generated/traceProcessorVersion")
    inputs.property("version", version)
    outputs.dir(output)
    doLast {
        val file = output.get().file("io/embrace/analysis/perfetto/trace_processor_shell.version").asFile
        file.parentFile.mkdirs()
        file.writeText(version)
    }
}

sourceSets.main {
    resources.srcDir(traceProcessorVersion)
}
