// startup-tools: the SDK startup-analysis toolchain, ported from the Python skills under .claude/skills.
//
// One JVM module, one Clikt dispatcher, one file per former Python script under cli/, with everything
// those files share living once under core/, perfetto/, device/ and campaign/. Run it through the
// checked-in wrapper `tools/startup`, which builds this module incrementally and then execs the
// installDist launcher - NOT through `./gradlew run`, which swallows stdin, turns exit codes into
// exceptions, breaks terminal detection, and would wrap a Gradle daemon around the tool's own
// `gradlew -p examples/ExampleApp` children for the length of a campaign.
//
// The repo's JVM convention is applied deliberately: it brings detekt with the repo config, the
// stdlib pin, JUnit 4, and warnings-as-errors. It also pins the JVM target to 11 and the language
// version to 2.0 - a pending decision (plan §6, item 4) may move this module to JVM 17; until then the
// shared convention keeps the build consistent with every other JVM module here.
plugins {
    id("embrace-jvm-conventions")
    application
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.clikt)
    implementation(libs.kotlinx.serialization.json)
}

application {
    applicationName = "startup-tools"
    mainClass.set("io.embrace.startup.MainKt")
}

// Opt-in Layer-B gate: `./gradlew -PtraceParity=1 :startup-tools:test` re-runs every query through the
// native trace_processor_shell on every captured fixture trace and compares the rows with the frozen
// Python output. Needs the traces on disk (they are not in the repo) and re-parses each one nine
// times, so it is off by default. Read through a provider so the configuration cache stays valid.
tasks.withType<Test>().configureEach {
    systemProperty("startup.traceParity", providers.gradleProperty("traceParity").orElse("").get())
}
